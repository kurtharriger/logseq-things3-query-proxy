(ns app.main.http
  (:require
    [applied-science.js-interop :as j]
    [cljs.reader :refer [read-string]]
    [clojure.string :as string]
    [com.wsscode.async.async-cljs :as wa :refer [go-promise  <?maybe]]
    [macchiato.middleware.params :as params]
    [macchiato.middleware.restful-format :as rf]
    [macchiato.server :as http]
    [reitit.ring :as ring]
    [reitit.ring.coercion :as rrc]
    ;; [reitit.ring.middleware.exception :as exception] ;clj only
    [taoensso.timbre :refer [info error trace debug]]
    [app.main.db :as db]))


(defmethod rf/deserialize-request "application/edn"
  [{:keys [body]}]
  (debug body)
  (try
    (read-string (str body))
    (catch :default err
      (error err))))


(defmethod rf/serialize-response "application/edn"
  [{:keys [body]}]
  (debug body)
  (pr-str body))


(defn query-db
  [request callback]
  (trace request)
  (let [{:keys [db query inputs]} (:body request)
        db-name (keyword db)
        db (case db
             :demo db/demo-db
             db/default-db)]
    (info db-name query inputs)
    (if db
      (go-promise
        (try
          (<?maybe (db/load-datascript! db))
          (info "data loaded")
          (let [result (apply (partial db/query db query) inputs)]
            (callback {:status 200
                       :body {:data result}}))
          (catch js/Error err
            (callback {:status 500 :body {:message (j/get err :message)}}))))
      (callback {:status 404 :body (str "no such db" db-name)}))))


(defn accept-edn?
  [request]
  (string/includes? (get-in request [:headers "accept"]) "application/edn"))


(defn dump-db
  [request callback]
  (debug request)
  (let [db-name (get-in request [:query-params "db"])
        db-name (keyword db-name)
        db (case db-name
             :demo db/demo-db
             db/default-db)]
    (info db-name db)
    (if db
      (go-promise
        (try
          (<?maybe (db/load-datascript! db))
          (info "data loaded")
          (let [result (db/dump-db db)
                encoded (if (accept-edn? request) result (pr-str result))]
            (callback {:status 200
                       :body {:data encoded}}))
          (catch js/Error err
            (callback {:status 500 :body {:message (j/get err :message)}}))))
      (callback {:status 404 :body (str "no such db" db-name)}))))


(def routes [["/db" {:get
                     {:handler #'dump-db}}]
             ["/query" {:post
                        {:handler #'query-db}}]])


;; https://github.com/metosin/reitit/blob/master/doc/ring/ring.md
(def router (ring/router routes))


(def handler
  (ring/ring-handler
    router
    nil ; default-handler
    {:middleware [;; exception/exception-middleware ; clj only
                  rrc/coerce-request-middleware
                  rrc/coerce-response-middleware
                  params/wrap-params
                  #(rf/wrap-restful-format % {:keywordize? true
                                              ;; requires a set
                                              :content-types #{"application/json"
                                                               "application/transit+json"
                                                               "application/edn"}
                                              ;; requires vector
                                              :accept-types ["application/json"
                                                             "application/transit+json"
                                                             "application/edn"]})]}))


(defn start-server!
  [{:keys [on-started on-error port public?]}]
  (let [host (if public? "0.0.0.0" "127.0.0.1")
        port (or port 7891)
        on-started (or on-started (constantly nil))
        on-error (or on-error (constantly nil))
        server
        (http/start
          {:handler    #'handler
           :host       host
           :port       port
           :on-success
           (fn [& args]
             (info "http server started on" host ":" port)
             (on-started host port))})]
    (j/call server :on "error"
      (fn [err]
        (error "http server error " err)
        (j/call server :close)
        (on-error err)))
    server))
