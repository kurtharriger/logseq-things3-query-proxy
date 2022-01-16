(ns things.core
  (:require ["fs" :as fs]
            ["http" :as http]
            ["os" :as os]
            ["sqlite3" :as sql]
            [applied-science.js-interop :as j]
            [cljs.core :refer [js->clj]]
            [cljs.core.async :as async :refer-macros [go] :refer [<! >!]]
            [cljs.reader :refer [read-string]]
            [clojure.pprint :refer [pprint]]
            [clojure.string]

            [com.rpl.specter :as s :refer-macros [select transform]]
            [clojure.tools.cli :refer [parse-opts]]
            [datascript.core :as d]))

;(def default-db "db/main.sqlite")
(def default-db "~/Library/Group Containers/JLMPQHK86H.com.culturedcode.ThingsMac/Things Database.thingsdatabase/main.sqlite")
(def area-query       "SELECT * FROM TMArea;")
; tasks may have other tasks as projects and datascript 
; and thus when loading into datascript the "project"
; tasks must be loaded before tasks that reference them
; note: a project task may be trashed without its child tasks being 
; marked as trashed which will cause datascript to complain that
; referenced project does not exist. Thus if excluding trashed
; need to revise query to join parent and ensure it is not trashed
; or otherwise handle this case when loading data into datascript
; for now I'll just return all data and filter trashed rows in 
; datascript
(def task-query       "SELECT * FROM TMTask order by project")
(def checklist-query  "SELECT * FROM TMChecklistItem;")

(defn write-chan [ch err data]
  (when err (println err))
  (go
    (>! ch [err data])
    (async/close! ch)))

(defn asyncify [o m]
  (fn [& args]
    (let [ch (async/chan)
          cb (partial write-chan ch)]
      (apply j/call o m (concat args [cb]))
      ch)))

(defn select-all* [db query] ((asyncify db :all) query))

(defn select-all [db query nskw]
  (go (-> (<! (select-all* db query))
          (js->clj :keywordize-keys true)
          (->>
           (s/setval [s/LAST s/ALL s/MAP-VALS nil?] s/NONE)
           (s/transform [s/LAST s/ALL s/MAP-KEYS s/NAMESPACE] (constantly (name nskw)))))))

(defn get-areas [db]
  (go (->> (<! (select-all db area-query :things.area))
           (s/transform [s/LAST s/ALL :things.area/cachedTags] str))))

(defn get-tasks [db]
  (go (->> (<! (select-all db task-query :things.task))
           ; replace blob buffers with strings
           (s/transform [s/LAST s/ALL :things.task/cachedTags] str)
           (s/transform [s/LAST s/ALL :things.task/recurrenceRule] str)
           (s/transform [s/LAST s/ALL :things.task/repeater] str)
           (s/transform [s/LAST s/ALL (s/must :things.task/area)] #(do [:things.area/uuid %]))
           (s/transform [s/LAST s/ALL (s/must :things.task/project)] #(do [:things.task/uuid %])))))

(defn get-checklists [db]
  (go (->> (<! (select-all db checklist-query :things.checklist))
           (s/transform [s/LAST s/ALL (s/must :things.checklist/task)] #(do [:things.task/uuid %])))))

(defn get-things-data [db]
  ; note order is important if for loading into datascript
  (go (concat (last (<! (get-areas db)))
              (last (<! (get-tasks db)))
              (last (<! (get-checklists db))))))

(defn pretty-str [data]
  (with-out-str (pprint data)))

(defn request-handler [db req res]
  (go
    (let [data (<! (get-things-data db))]
      (j/call res :writeHead 200 #js {"Content-Type" "application/edn"})
      (j/call res :end (pretty-str (vec data))))))

; a place to hang onto the server so we can stop/start it in repl
(defonce server-ref
  (volatile! nil))

(defn start-server [db port]
  (let [server (http/createServer #(request-handler db %1 %2))]
    (.listen server port
             (fn [err]
               (if err
                 (js/console.error "server start failed")
                 (js/console.info (str "http server running at http://localhost:" port)))))

    (vreset! server-ref server)))


(defn open-db [db-path]
  (let [homedir (j/call os :homedir)
        db-path (clojure.string/replace db-path #"^~" homedir)
        exists (j/call fs :existsSync db-path)]
    (when exists (sql/Database. db-path))))



(def cli-options
  ;; An option with a required argument
  [["-p" "--port PORT" "Port number"
    :id :port
    :default 7980
    :parse-fn read-string
    :validate [#(and (int? %) (< 1025 % 0x10000)) "Must be a number between 1025 and 65536"]]
   ;; A non-idempotent option (:default is applied first)
   ["-d" "--database DB" "Database file"
    :id :db
    :default default-db]
   ["-h" "--help"]])

(defn main [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)
        {:keys [port db help]} options]
    (if (or errors help)
      (do (when errors (println errors)) (println summary))
      (if-let [conn (open-db db)]
        (start-server conn port)
        (println "Database not found" db)))))

(do  ;repl helpers
  (defonce db (open-db default-db))

  (defn pchan [ch] (go (pprint (<! ch))))
  (defn test-db [db] (select-all* db "select 1 from TMTask limit 1"))

  )