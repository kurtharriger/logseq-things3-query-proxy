(ns app.main.db
  (:require
    ["fs" :as fs]
    ["os" :as os]
    ["sqlite3" :as sql]
    [applied-science.js-interop :as j]
    [clojure.string :as string]
    [clojure.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [com.rpl.specter :as s :refer-macros [select transform]]
    [com.wsscode.async.async-cljs :as wa :refer [go-promise  <?maybe]]
    [datascript.core :as d]
    [medley.core :as medley]
    [taoensso.timbre :refer [info error trace debug]]))


(defn coerce-date
  [unix]
  (when (and unix (pos? unix))
    (js/Date. (* 1000 unix))))


(defn as-ref
  [target-key]
  (fn [target-id] (when target-id [target-key target-id])))


(defn buffer?
  [v]
  (instance? js/Buffer v))


(defn remove-nil-values
  [data]
  (if (sequential? data)
    (s/setval [s/ALL s/MAP-VALS nil?] s/NONE data)
    (s/setval [s/MAP-VALS nil?] s/NONE data)))


(defn query
  [row-transform sql db]
  (go-promise
    (let [data (<?maybe
                 (new js/Promise
                   (fn [resolve]
                     (j/call db :all sql (fn [err data]
                                           (if err (resolve err) (resolve data)))))))
          data (js->clj data :keywordize-keys true)
          ;; remove keys with nil values
          data (remove-nil-values data)
          data (mapv row-transform data)
          ;; again remove nils as some transforms may have removed values
          data (remove-nil-values data)]

      data)))


(defn setkeyns
  "Update keys in the data map to have the specified namespace. 
   
  (setkeyns row \"things.task\")"
  [data ns]
  (s/setval [s/MAP-KEYS s/NAMESPACE] ns data))


(defn get-tags
  [db])


(defn area-transform
  [area]
  (-> area
    (dissoc  :cachedTags)
    (setkeyns "things.area")))


(def get-areas (partial query area-transform "select * from TMArea order by 'index'"))


(defn task-transform
  [ns row]
  (-> row
    (dissoc :cachedTags)
    (dissoc :type)
    (update :recurrenceRule str)
    (update :repeater str)
    (update :creationDate coerce-date)
    (update :userModificationDate coerce-date)
    (update :dueDate coerce-date)
    (update :startDate coerce-date)
    (update :stopDate coerce-date)
    (update :instanceCreationStartDate coerce-date)
    (update :afterCompletionReferenceDate coerce-date)
    (update :lastAlarmInteractionDate coerce-date)
    (update :todayIndexReferenceDate coerce-date)
    (update :nextInstanceStartDate coerce-date)
    (update :dueDateSuppressionDate coerce-date)
    (update :repeaterMigrationDate coerce-date)
    (update :area (as-ref :things.area/uuid))
    (update :project (as-ref :things.project/uuid))
    (update :actiongroup (as-ref :things.actiongroup/uuid))
    (update :repeatingTemplate (as-ref :things.task/uuid))
    (setkeyns ns)))


(def get-projects (partial query (partial task-transform "things.project") "select * from TMTask where type = 1 order by 'index'"))


;; action group = project headers
(def get-action-groups (partial query (partial task-transform "things.actiongroup") "select * from TMTask where type = 2 order by 'index'"))


;; also ordered by repeatingTemplate to ensure that any templates are imported before they are used
(def get-tasks (partial query (partial task-transform "things.task") "select * from TMTask where type = 0 order by repeatingTemplate, 'index'"))


(defn checklistitem-transform
  [row]
  (-> row
    (update :creationDate coerce-date)
    (update :userModificationDate coerce-date)
    (update :stopDate coerce-date)
    (update :task (as-ref :things.task/uuid))
    (setkeyns "things.checklistitem")))


(def get-checklistitems (partial query checklistitem-transform "select * from TMChecklistItem order by 'index'"))


;; todo task tags

;;


(defn create-datascript-conn
  []
  (d/create-conn
    {:things.area/uuid {:db/unique :db.unique/identity}

     :things.project/uuid {:db/unique :db.unique/identity}
     :things.project/area {:db/valueType :db.type/ref}

     :things.actiongroup/uuid {:db/unique :db.unique/identity}
     :things.actiongroup/area {:db/valueType :db.type/ref}
     :things.actiongroup/project {:db/valueType :db.type/ref}

     :things.task/uuid {:db/unique :db.unique/identity}
     :things.task/area {:db/valueType :db.type/ref}
     :things.task/project {:db/valueType :db.type/ref}
     :things.task/repeatingTemplate {:db/valueType :db.type/ref}

     :things.checklistitem/uuid  {:db/unique :db.unique/identity}
     :things.checklistitem/task {:db/valueType :db.type/ref}}))


(defn load-datascript!
  [conn db]
  (go-promise
    (let [areas (<?maybe (get-areas db))
          projects (<?maybe (get-projects db))
          actiongroups (<?maybe (get-action-groups db))
          tasks (<?maybe (get-tasks db))
          checklistitems (<?maybe (get-checklistitems db))]
      (try
        (d/transact! conn
          (concat areas
            projects
            actiongroups
            tasks
            checklistitems))
        (info "Added " (count (d/datoms @conn :eavt)) "datums.")
        (catch :default e
          (do (error e)
              (throw (ex-info "Error populating datascript db" {} e))))))))


(defn open-db
  [db-path]
  (let [homedir (j/call os :homedir)
        db-path (clojure.string/replace db-path #"^~" homedir)
        exists (j/call fs :existsSync db-path)]
    (if exists (sql/Database. db-path)
        (throw (ex-info "database not found" {:path db-path})))))


(def default-db-path "~/Library/Group Containers/JLMPQHK86H.com.culturedcode.ThingsMac/Things Database.thingsdatabase/main.sqlite")


;; copied Things.sqlite3 from https://github.com/AlexanderWillner/things.sh 7104771af191eec196e4dff087ece02618b05e4c
(def demo-db-path "resources/Things.sqlite3")


(defonce default-db (delay (open-db default-db-path)))
(defonce datascript-conn (create-datascript-conn))


(defn begin-polling-changes!
  ([]
   (begin-polling-changes! datascript-conn @default-db))
  ([conn db]
   (async/go-loop []
     (try (<?maybe (load-datascript! conn db)) (catch :default _ nil))
     (<?maybe (async/timeout 1000))
     (recur))))


(comment
  (defonce demodb (open-db demo-db-path))
  (defonce db (open-db default-db-path))

  (count (:eavt @datascript-conn))
;; 
  (def last-gp (volatile! nil))

  (defn gp [value & args]
    (go-promise (let [r (try (<?maybe
                              (if (ifn? value) (apply value args) value))
                             (catch :default e e))]
                  (vreset! last-gp r)
                  (pprint r)
                  r))))
