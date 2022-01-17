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
           (s/transform [s/LAST s/ALL (s/must :things.area/cachedTags)] str))))

(defn coerce-date [unix]
  (when (and unix (pos? unix))
    (js/Date. (* 1000 unix))))

(defn as-ref [target-key ] 
  (fn [target-id] [target-key target-id]))

(defn get-tasks [db]
  ; todo: transducer
  (go (->> (<! (select-all db task-query :things.task))
           ; replace blob buffers with strings
           (s/transform [s/LAST s/ALL (s/must :things.task/cachedTags)] str)
           (s/transform [s/LAST s/ALL (s/must :things.task/recurrenceRule)] str)
           (s/transform [s/LAST s/ALL (s/must :things.task/repeater)] str)
           (s/transform [s/LAST s/ALL (s/must :things.task/creationDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/userModificationDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/dueDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/startDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/stopDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/instanceCreationStartDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/afterCompletionReferenceDate)] coerce-date)
           ;todo is this a ate?
           (s/transform [s/LAST s/ALL (s/must :things.task/alarmTimeOffset)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/lastAlarmInteractionDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/todayIndexReferenceDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/nextInstanceStartDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/dueDateSuppressionDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/repeaterMigrationDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.task/area)] (as-ref :things.area/uuid))
           (s/transform [s/LAST s/ALL (s/must :things.task/project)] (as-ref :things.task/uuid))
           ;todo need to ensure these reference tasks appear before tasks that reference them
           ;(s/transform [s/LAST s/ALL (s/must :things.task/repeatingTemplate)] (as-ref :things.task/uuid)
           
           
           
           )
      
      ))

(defn get-checklists [db]
  (go (->> (<! (select-all db checklist-query :things.checklist))
           (s/transform [s/LAST s/ALL (s/must :things.checklist/task)] (as-ref :things.task/uuid))
           (s/transform [s/LAST s/ALL (s/must :things.checklist/creationDate)] coerce-date)
           (s/transform [s/LAST s/ALL (s/must :things.checklist/userModificationDate)] coerce-date))))

(defn get-things-data [db]
  ; note order is important if for loading into datascript
  (go 
    (->>
     (concat (last (<! (get-areas db)))
             (last (<! (get-tasks db)))
             (last (<! (get-checklists db))))
     (s/setval [s/ALL s/MAP-VALS nil?] s/NONE))))

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

(defonce ds-db
  (d/create-conn
   {:things.area/uuid {:db/unique :db.unique/identity}
    :things.task/uuid {:db/unique :db.unique/identity}
    :things.checklist/uuid  {:db/unique :db.unique/identity}
    :things.task/area {:db/valueType :db.type/ref}
    :things.task/project {:db/valueType :db.type/ref}
    ; todo: order of load is important otherwise it will error
    ;:things.task/repeatingTemplate {:db/valueType :db.type/ref}
    :things.checklist/task {:db/valueType :db.type/ref}}))

(defn load-datascript! [db]
  (go (d/transact! ds-db (<! (get-things-data db)))))

(comment  ;repl helpers
  (defonce db (open-db default-db))

  (load-datascript! db)
  (defn pchan [ch] (go (pprint (<! ch))))
  (defn test-db [db] (select-all* db "select 1 from TMTask limit 1"))

  
  )