(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter Tray Menu ipcMain]]
            ["path" :as path]
            [app.common.ipc :as ipc]
            [app.main.http :as http]
            [cljs.reader :refer [read-string]]

            [taoensso.timbre :refer [info debug error trace]]
            [applied-science.js-interop :as j]
            [cljs.core.async  :as async :refer [put! go-loop <!]]))


;; todo spec
;; main-window
;; system-tray
;; server/status
;; server/host
;; server/port
(defonce state (atom {}))


(defn create-main-window!
  []
  (let [window
        (BrowserWindow.
          (clj->js {:width 800
                    :height 600
                    :show false
                    :webPreferences
                    {;; :nodeIntegration true
                     ;; todo: also need contextBridge to access ipcRenderer
                     ;; if this is enabled
                     :contextIsolation false
                     :preload (path/join js/__dirname "/preload.js")}}))]
    (j/call window :loadURL (str "file://" js/__dirname "/public/index.html"))
    (j/call window :on "close"
      (fn [e]
        (j/call e :preventDefault)
        (j/call window :hide)))
    (swap! state assoc :main-window window)
    window))


(defn show-main-window!
  []
  (assert (:main-window @state) "main-window not created")
  (j/call (:main-window @state) :show))


(defn create-tray!
  []
  (assert (nil? (:system-tray @state)) "create-tray! should only be called once")
  ;; icon from https://www.flaticon.com/uicons?word=task
  (let [tray (Tray. "resources/public/img/tray.png")]
    (j/call tray :on "click" show-main-window!)
    (swap! state assoc :system-tray tray)
    tray))


(defn handle-quit!
  []
  (j/call (:main-window @state) :destroy)
  (j/call (:system-tray @state) :destroy)
  (j/call app :quit))


(defn handle-get-server-status
  []
  (-> (select-keys @state [:server/status :server/port :server/host :server/error])
    (update :server/error #(j/get % :message))))


(defn start-server!
  []
  (info "start-server")

  (swap! state assoc :server
    (http/start-server!
      {:on-started (fn [host port]
                     (swap! state merge {:server/status :running :server/port port :server/host host}))
       :on-error (fn [err]
                   (error "Server failed to start" err)
                   (swap! state merge {:server/status :failed :server/error err}))})))


(defn ready!
  []
  (debug "ready!")
  (create-main-window!)
  (create-tray!)
  (info "ready"))


(defn register-crash-reporter
  []
  ;; (.start crashReporter
  ;;         (clj->js
  ;;          {:companyName "MyAwesomeCompany"
  ;;           :productName "MyAwesomeApp"
  ;;           :submitURL "https://example.com/submit-url"
  ;;           :autoSubmit false}))
  )


(defn main
  []
  (debug "main")

  (ipc/register-ipc-handler! ipcMain :quit! #'handle-quit!)
  (ipc/register-ipc-handler! ipcMain :get-server-status #'handle-get-server-status)

  (start-server!)
  (.on app "ready" ready!))


;; devtools
(defn before-load-async
  []
  (debug "before-load-async"))


(defn after-load
  []
  (debug "after-load"))
