(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter Tray Menu ipcMain]]
            ["path" :as path]
            [macchiato.server :as http]
            [taoensso.timbre :refer [info]]
            [applied-science.js-interop :as j]
            [cljs.core.async  :as async :refer [put! go-loop <!]]))


(defn create-browser!
  []
  (let [ch (async/chan)
        window
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
        (j/call e :preventDefault true)
        (j/call window :hide)
        ;; (put! ch [:quit])
        ))
    {:window window
     :ch ch}))


(defn create-tray!
  [on-click]
  ;; icon from https://www.flaticon.com/uicons?word=task
  (let [tray (Tray. "resources/public/img/tray.png")]
    (j/call tray :on "click" on-click)
    tray))


(defn show-browser
  [window]
  (j/call window :show))


(defn start-ipc-listener!
  []
  (j/call ipcMain :on "message"
    (fn [_ msg] (println "received msg" msg))))


(def main-window (atom nil))
(def system-tray (atom nil))


(defn ready!
  []
  (start-ipc-listener!)
  (let [{:keys [window ch]} (create-browser!)
        tray (create-tray! (partial show-browser window))]
    ;; tray seems to disappear after about a minute when garbage collection
    ;; runs if not saved to global not sure why reference in go loop does not
    ;; prevent garbage collection
    (reset! main-window window)
    (reset! system-tray tray)
    (go-loop []
      (let [msg (<! ch)]
        (when (= :quit (first msg))
          (j/call window :destroy)
          (j/call tray :destroy)
          (j/call app :quit)))
      (recur))))


(defn handler
  [request callback]
  (callback {:status 200
             :body "Hello Macchiato"}))

(defn server []
  (info "Hey I am running now!")
  (let [host "127.0.0.1"
        port 3000]
    (http/start
     {:handler    handler
      :host       host
      :port       port
      :on-success #(info "macchiato-test started on" host ":" port)})))

(defn main
  []
  ;; CrashReporter can just be omitted
  ;; (.start crashReporter
  ;;         (clj->js
  ;;          {:companyName "MyAwesomeCompany"
  ;;           :productName "MyAwesomeApp"
  ;;           :submitURL "https://example.com/submit-url"
  ;;           :autoSubmit false}))

  ;; (.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
  ;;                                 (.quit app)))
  (server)
  (j/call app :on "ready" ready!))
