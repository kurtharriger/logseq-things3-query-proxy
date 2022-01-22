(ns app.renderer.core
  (:require
   ; note ipcRender must be added via preload.
   ; standard require does not work
  ; ["electron" :refer [ipcRenderer]]
   [reagent.core :refer [atom]]
   [reagent.dom :as rd]
   [applied-science.js-interop :as j]
   [cljs.core.async  :as async :refer [put! go-loop <!]]
   ["peerjs" :as peer]))

(enable-console-print!)

(defonce state (atom 0))

(defn send-message! []
  (j/call js/ipcRenderer :send "message" "test" ))

(defn on-click []
  (send-message!)
  (swap! state inc))

(defn root-component []
  [:div
   [:div.logos
    [:img.electron {:src "img/electron-logo.png"}]
    [:img.cljs {:src "img/cljs-logo.svg"}]
    [:img.reagent {:src "img/reagent-logo.png"}]]
   [:button
    {:on-click on-click}
    (str "Clicked " @state " times")]])

(defn ^:dev/after-load start! []
  (rd/render
   [root-component]
   (js/document.getElementById "app-container")))
