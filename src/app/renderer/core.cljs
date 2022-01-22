(ns app.renderer.core
  (:require
    ;; note ipcRender must be added via preload.
    ;; standard require does not work when webPreferences.nodeIntegration is false 
    ;; ["electron" :refer [ipcRenderer]]
    [cljs.reader :refer [read-string]]
    [reagent.core :refer [atom]]
    [reagent.dom :as rd]
    [applied-science.js-interop :as j]
    [taoensso.timbre :refer [info]]
    [cljs.core.async  :as async :refer [put! go-loop <!]]))


(enable-console-print!)

(defonce state (atom {:server/status :unknown}))


(defn root-component
  [on-quit]
  [:div
   [:h1 "Things Query Proxy"]
   [:p (str "Service is status " (name (:server/status @state)))]
   [:button
    {:on-click on-quit} "Quit"]])


(defn send-message!
  [msg & args]
  (let [payload (pr-str (vec (concat [msg] args)))]
    (info "sending " payload)
    (j/call js/ipcRenderer :send "message" payload)))


(defmulti handle-ipc-message! (fn [msg & args] msg))


(defmethod handle-ipc-message! :update-server-state [_ & server-state]
  (swap! state merge (select-keys server-state [:server/status :server/port])))


(defn start-ipc-listener!
  []
  (j/call js/ipcRenderer :on "message"
    (fn [_ payload]
      (let [args (read-string payload)]
        (info "received" payload)
        (apply handle-ipc-message! args)))))


(defn main!
  []
  (info "main!")
  (start-ipc-listener!)
  (let [on-quit (send-message! :quit)]
    (rd/render
      [root-component on-quit]
      (js/document.getElementById "app-container"))))


(defn ^:dev/before-load before-hot-reload!
  []
  (info "before-hot-reload!"))


(defn ^:dev/after-load after-hot-load!
  []
  (info "before-hot-reload!"))

