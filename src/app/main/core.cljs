(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter Tray Menu]]
            [applied-science.js-interop :as j]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise  <? <?maybe]]))

(def main-window (atom nil))
(def tray (atom nil))

(defn init-browser []
  (reset! main-window (BrowserWindow.
                        (clj->js {:width 800
                                  :height 600
                                  :show false
                                  :webPreferences
                                  {:nodeIntegration true}})))
  ; Path is relative to the compiled js file (main.js in our case)
  (.loadURL ^js/electron.BrowserWindow @main-window (str "file://" js/__dirname "/public/index.html"))
  (.on ^js/electron.BrowserWindow @main-window "closed" #(reset! main-window nil)))



(defn create-tray [on-click]
  (let [tray (Tray. "resources/public/img/tray.png")]
    (j/call tray :on "click" on-click)
    tray))

(defn init-tray! []
  (swap! tray (fn [tray]
                (when tray (j/call @tray :destroy))
                (create-tray init-browser))))

(defn main []
  ; CrashReporter can just be omitted
  ;; (.start crashReporter
  ;;         (clj->js
  ;;          {:companyName "MyAwesomeCompany"
  ;;           :productName "MyAwesomeApp"
  ;;           :submitURL "https://example.com/submit-url"
  ;;           :autoSubmit false}))

  (.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                  (.quit app)))
  (.on app "ready" init-tray!))

;; icon from https://www.flaticon.com/uicons?word=task


(comment 
  
  )