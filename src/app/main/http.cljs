(ns app.main.http
  (:require
    [applied-science.js-interop :as j]
    [cljs.reader :refer [read-string]]
    [macchiato.middleware.params :as params]
    [macchiato.middleware.restful-format :as rf]
    [macchiato.server :as http]
    [reitit.ring :as ring]
    [reitit.ring.coercion :as rrc]
    ;; [reitit.ring.middleware.exception :as exception] ;clj only
    [taoensso.timbre :refer [info error trace debug]]))


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
  (debug request)
  (let [query (:body request)]
    (callback {:status 200
               :body [query]})))


(defn dump-db
  [request callback]
  (trace request)
  (debug  (-> request :body))
  (callback {:status 200
             :body "Hello Macchiato2"}))


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
        port (or port 3000)
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
