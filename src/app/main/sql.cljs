(ns app.main.sql
  (:require
   ["fs" :as fs]
   ["os" :as os]
   ["sqlite3" :as sqlite]
   ["fast-xml-parser" :as xml]
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

(defn remove-attributes-with-default-values [defaults target]
  (assert (map? defaults) "expecting defaults as map")
  (assert (map? target) "expecting target to be a map")
  (let [default-kvpairs (set (map vec defaults))
        kvpairs (map vec target)]
    (into {} (filter (complement default-kvpairs) kvpairs))))

(defn remove-computable-attributes [attrs m]
  (assert (set? attrs) "expecting a set")
  (medley/remove-keys attrs m))



(defn get-tags
  [db])

(defn area-transform
  [area]
  (-> area
      (dissoc  :cachedTags)
      (->> (remove-attributes-with-default-values {:index 0}))
      (setkeyns "things.area")))

(def get-areas (partial query area-transform "select * from TMArea order by 'index'"))

(defn status-keyword [value]
  (get {0 :open
  ; 1 doesn't appear to be used
        2 :cancelled
        3 :completed} value :unknown))

(defn start-keyword [value]
  (get {0 :not-started
        1 :started
        2 :postponed} value :unknown))

(defn task-transform
  [ns row]
  (-> row
      (dissoc :type)

      ; not useful
      (dissoc :delegate)
      (dissoc :repeater)
      (dissoc :repeaterMigrationDate)
      (dissoc :repeaterRegularSlotDatesCache)
      (dissoc :notesSync)
      (dissoc :cachedTags)

      (->> (remove-computable-attributes
            #{:checklistItemsCount
              :instanceCreationCount
              :openChecklistItemsCount
              :openUntrashedLeafActionsCount
              :untrashedLeafActionsCount}))

      (update :actiongroup (as-ref :things.actiongroup/uuid))
      (update :afterCompletionReferenceDate coerce-date)
      (update :area (as-ref :things.area/uuid))
      (update :creationDate coerce-date)
      (update :dueDate coerce-date)
      (update :dueDateSuppressionDate coerce-date)
      (update :instanceCreationStartDate coerce-date)
      (update :lastAlarmInteractionDate coerce-date)
      ; not entirely sure what this means
      ; at first I thought it might be trashed
      ; but there is field for trashed and its not 
      ; often true when this is set
      ; might be more related to items reoccuring 
      ; items as I see checklists have this set
      ; on completed reoccuring tasks. I thus think
      ; it is info that can be derived 
      ;(update :leavesTombstone pos?)
      (dissoc :leavesTombstone)
      (update :nextInstanceStartDate coerce-date)
      (update :project (as-ref :things.project/uuid))
      (update :recurrenceRule str)
      (update :repeater str)
      (update :repeaterMigrationDate coerce-date)
      (update :repeatingTemplate (as-ref :things.task/uuid))
      (update :start start-keyword)
      (update :startDate coerce-date)
      (update :status status-keyword)
      (update :stopDate coerce-date)
      (update :todayIndexReferenceDate coerce-date)
      (update :trashed pos?)
      (update :userModificationDate coerce-date)

      ; the reoccurenceRule is some xml gibberish that I assume indicates
      ; how to compute the next date but the encoding is somewhat cryptic 
      ; and not currently that useful.  If it is needed I would rather
      ; parse it into something useful than export it as is and thus
      ; rather not export it at all so that I can use the attribute name
      ; later if needed.  not empty seems more expressive than seq as its a
      (as-> t (assoc t :repeating? (or (not (nil? (:repeatingTemplate t))) (not (string/blank? (:recurrenceRule t))))))
      (dissoc :recurrenceRule)

      ; note: easier to put after transforms particularly for 
      ; :repeater str
      (->> (remove-attributes-with-default-values
            {:dueDateOffset 0
             :index 0
             :instanceCreationPaused 0
             :leavesTombstone false
             :notes ""
             :recurrenceRule ""
             :repeater ""
             :repeating? false
             :startBucket 0
             :todayIndex 0
             :title ""
             :trashed false}))
      (setkeyns ns)))

(defn project-transform [project]
  (-> (task-transform "things.project" project)))

(def get-projects (partial query project-transform "select * from TMTask where type = 1 order by 'index'"))


;; action group = project headers
(def get-action-groups (partial query (partial task-transform "things.actiongroup") "select * from TMTask where type = 2 order by 'index'"))

; Thought about maybe importing repeatingTemplate tasks with different entity namespace, but it appears 
; repeating tasks also appear to be regular tasks with additional info about when they repeat and checklists
; can reference these as well so if it had a seperate namespace would need to determine correct ref when 
; importing checklists as such it seems easiest to treat these as tasks with more info, but when doing import 
; need to ordered by repeatingTemplate to ensure that any templates (having null repeatingTemplate) are imported
; before any tasks that reference them or datascript will complian
(def get-tasks (partial query (partial task-transform "things.task") "select * from TMTask where type = 0 order by repeatingTemplate, 'index'"))


(defn checklistitem-transform
  [row]
  (-> row
      (update :creationDate coerce-date)
      (update :userModificationDate coerce-date)
      (update :status status-keyword)
      (update :stopDate coerce-date)
      (update :task (as-ref :things.task/uuid))
      ; appears to be true for completed tasks associated
      ; with items in logbook.  Deleted checklists
      ; appear to be actually removed from db
      (dissoc :leavesTombstone)
      (->> (remove-attributes-with-default-values
            {:index 0
             :title ""
             :leavesTombstone false}))
      (setkeyns "things.checklistitem")))


(def get-checklistitems (partial query checklistitem-transform "select * from TMChecklistItem order by 'index'"))


;; todo task tags

(defn sqldb-path
  [path]
  (let [homedir (j/call os :homedir)
        path (clojure.string/replace path #"^~" homedir)]
    (and (j/call fs :existsSync path) path)))


(defn open-db
  [path]
  (if-let [db-path (sqldb-path path)]
    (sqlite/Database. db-path)
    (throw (ex-info "database not found" {:path path}))))


(defn snapshot
  [db]
  (go-promise
   (let [areas (<?maybe (get-areas db))
         projects (<?maybe (get-projects db))
         actiongroups (<?maybe (get-action-groups db))
         tasks (<?maybe (get-tasks db))
         checklistitems (<?maybe (get-checklistitems db))]
     (concat areas
             projects
             actiongroups
             tasks
             checklistitems))))

(def test-demo-db "resources/Things.sqlite3")
(def things-db-path "~/Library/Group Containers/JLMPQHK86H.com.culturedcode.ThingsMac/Things Database.thingsdatabase/main.sqlite")

(comment
  ;(go-promise (def data (<! (snapshot (open-db test-demo-db)))))
  (go-promise (def data (<! (snapshot (open-db things-db-path))))))