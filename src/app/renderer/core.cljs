(ns app.renderer.core
  (:require
    ;; note ipcRender must be added via preload.
    ;; standard require does not work when webPreferences.nodeIntegration is false 
    ;; ["electron" :refer [ipcRenderer]]
    [app.common.ipc :as ipc]
    [reagent.core :refer [atom]]
    [reagent.dom :as rd]
    [applied-science.js-interop :as j]
    [com.wsscode.async.async-cljs :as wa :refer [go-promise <?maybe]]
    [taoensso.timbre :refer [info debug]]))


(enable-console-print!)

(defonce state (atom {:server/status :unknown}))

(def ipcRenderer js/ipcRenderer)


(defn quit!
  []
  (ipc/invoke ipcRenderer :quit!))


(defn request-server-status!
  []
  (go-promise
    (let [status (<?maybe (ipc/invoke ipcRenderer :get-server-status))]
      (swap! state merge status))))


(defn root-component
  []
  [:div
   [:h1 "Things Query Proxy"]
   (case (:server/status @state)
     :running
     [:p (str "Service is running on " (:server/host @state) ":" (:server/port @state))]

     :failed
     [:<> [:p "Service failed to start"] [:p (:server/error @state)]]

     [:p "Server status is unknown"])


   [:button
    {:on-click quit!} "Quit"]])


(defn main!
  []
  (debug "main!")
  (request-server-status!)
  (rd/render
    [root-component]
    (js/document.getElementById "app-container")))


(defn ^:dev/before-load before-hot-reload!
  []
  (debug "before-hot-reload!"))


(defn ^:dev/after-load after-hot-load!
  []
  (debug "before-hot-reload!"))

