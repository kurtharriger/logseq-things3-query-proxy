(ns app.main.http
  (:require
    [macchiato.server :as http]
    [applied-science.js-interop :as j]
    [taoensso.timbre :refer [info error]]))


(defn handler
  [request callback]
  (callback {:status 200
             :body "Hello Macchiato"}))


(defn start-server!
  [{:keys [on-started on-error port public?]}]
  (let [host (if public? "0.0.0.0" "127.0.0.1")
        port (or port 3000)
        on-started (or on-started (constantly nil))
        on-error (or on-error (constantly nil))
        server
        (http/start
          {:handler    (partial #'handler)
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
