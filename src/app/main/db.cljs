(ns app.main.db
  (:require
    [app.main.sql :as sql]
    [applied-science.js-interop :as j]
    [cljs.reader :refer [read-string]]
    [clojure.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [com.wsscode.async.async-cljs :as wa :refer [go-promise  <?maybe]]
    [datascript.core :as d]
    [taoensso.timbre :refer [info error trace debug]]))


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


;; sql-db
;; sql-db-path
;; datascript-conn

(defn things-db
  [path]
  {:sql-db-path path
   :sql-db (delay (sql/open-db path))
   :datascript-conn (create-datascript-conn)
   :listener-chans (atom {})})


(defn sql-db
  [things-db]
  @(:sql-db things-db))


(defn listen!
  [things-db key]
  (assert (nil? (-> things-db :listener-chans deref key)) "listener already registered")
  (let [ch (async/chan)]
    (swap! (:listener-chans things-db) assoc key ch)
    (d/listen! (:datascript-conn things-db) key
      (fn [{:keys [tx-data tempids]}]
        (when-not (empty? tx-data)
          (async/put! ch {:tx-id (:db/current-tx tempids)
                          :tx-data tx-data}))))
    ch))


(defn unlisten!
  [things-db key]
  (d/unlisten! (:datascript-conn things-db) key)
  (when-let [ch (-> things-db :listener-chans deref key)]
    (async/close! ch))
  (swap! (:listener-chans things-db) dissoc key))


(defn load-datascript!
  [things-db]
  (let [conn (:datascript-conn things-db)
        db (sql-db things-db)]
    (go-promise
      (try
        (d/transact! conn
          (<?maybe (sql/snapshot db)))
        (catch :default e
          (do (error e)
              (throw (ex-info "Error populating datascript db" {} e))))))))


(defn query
  [things-db query & inputs]
  (debug query inputs)
  (let [r (apply (partial d/q query @(:datascript-conn things-db)) inputs)]
    (debug r)
    r))


(defn dump-db
  [things-db]
  (let [conn (:datascript-conn things-db)]
    (pr-str @conn)))


(def default-db (things-db "~/Library/Group Containers/JLMPQHK86H.com.culturedcode.ThingsMac/Things Database.thingsdatabase/main.sqlite"))


;; copied Things.sqlite3 from https://github.com/AlexanderWillner/things.sh 7104771af191eec196e4dff087ece02618b05e4c
(def demo-db (things-db "resources/Things.sqlite3"))


(comment
  ;; (defonce demodb (open-db demo-db-path))
  ;; (defonce db (open-db default-db-path))

  ;; (count (:eavt @datascript-conn))
;; 
  (let [ch (listen! default-db :repl)]
    (async/go-loop []
      (when-let [changes (async/<! ch)]
        (pprint changes) (recur))
      (pprint "done.")))


  (def last-gp (volatile! nil))

  (defn gp [value & args]
    (go-promise (let [r (try (<?maybe
                              (if (ifn? value) (apply value args) value))
                             (catch :default e e))]
                  (vreset! last-gp r)
                  (pprint r)
                  r))))
