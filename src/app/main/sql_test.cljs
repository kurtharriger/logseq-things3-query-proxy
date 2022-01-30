(ns app.main.sql-test
  (:require [cljs.test :as test :refer (deftest is async)]
            [clojure.pprint :refer [pprint]]
            [clojure.data :refer [diff]]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise  <?maybe]]
            [app.main.sql :as sql]))


(defn area-ref
  [project]
  [:things.area/uuid (:things.area/uuid project)])


(defn project-ref
  [project]
  [:things.project/uuid (:things.project/uuid project)])


(defn heading-ref
  [heading]
  [:things.actiongroup/uuid (:things.actiongroup/uuid heading)])


(defn task-ref
  [task]
  [:things.task/uuid (:things.task/uuid task)])


(def area
  #:things.area{:uuid "C314E225-F216-4C21-B493-20ACB25CE517", :title "Area"})


(def someday-project
  #:things.project{:status :open
                   :uuid "DCA0C1B2-4E18-4507-92A1-E08E10E4886F"
                   :creationDate #inst "2017-12-27T18:23:48.825-00:00"
                   :userModificationDate #inst "2017-12-27T18:24:10.137-00:00"
                   :start :postponed
                   :title "Someday Project"})


(def next-project
  #:things.project{:status :open
                   :uuid "DED787E0-874A-4783-8F0F-0A02F87F8419"
                   :creationDate #inst "2017-12-27T18:24:14.122-00:00"
                   :userModificationDate #inst "2017-12-27T18:24:18.850-00:00"
                   :start :started
                   :title "Next Project"})


(def today-project
  #:things.project{:status :open
                   :uuid "52ADBAB5-A0EC-4D3F-BF83-2D578DAE3AF3"
                   :creationDate #inst "2017-12-27T18:24:22.391-00:00"
                   :userModificationDate #inst "2017-12-27T18:24:25.589-00:00"
                   :todayIndex -889
                   :index -344
                   :start :started
                   :startDate #inst "2017-12-27T00:00:00.000-00:00"
                   :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"
                   :title "Today Project"})


(def deleted-project
  #:things.project{:status :open
                   :trashed true
                   :uuid "B2732C07-F76F-4D3B-B4FC-17D7AA72C5E8"
                   :creationDate #inst "2017-12-27T18:25:06.943-00:00"
                   :userModificationDate #inst "2017-12-27T18:25:21.255-00:00"
                   :index -910
                   :start :started
                   :title "Deleted Project"})


(def project-in-area
  #:things.project{:status :open
                   :area [:things.area/uuid "C314E225-F216-4C21-B493-20ACB25CE517"]
                   :uuid "2CBF1FC7-BEEC-4256-A03F-71E13BD72FDC"
                   :creationDate #inst "2017-12-27T18:28:53.906-00:00"
                   :userModificationDate #inst "2017-12-27T18:28:57.392-00:00"
                   :start :started
                   :title "Project in Area"})


(def someday-project-heading
  #:things.actiongroup{:start :started
                       :title "Someday Project Heading"
                       :status :open
                       :project [:things.project/uuid "DCA0C1B2-4E18-4507-92A1-E08E10E4886F"]
                       :uuid "ACTIONGROUP-1899DC0E-63CB-4B9B-9AB6-B6894862B275"
                       :creationDate #inst "2017-12-27T18:23:56.581-00:00"
                       :userModificationDate #inst "2017-12-27T18:24:02.592-00:00"})


(def heading
  #:things.actiongroup{:start :started
                       :title "Heading"
                       :status :open
                       :project [:things.project/uuid "52ADBAB5-A0EC-4D3F-BF83-2D578DAE3AF3"]
                       :uuid "ACTIONGROUP-85660FE7-37EA-4B64-8A42-2745F2CC0C93"
                       :creationDate #inst "2017-12-27T18:25:37.623-00:00"
                       :userModificationDate #inst "2017-12-27T18:25:40.002-00:00"})


(def today-todo
  #:things.task{:status :open
                :project [:things.project/uuid "52ADBAB5-A0EC-4D3F-BF83-2D578DAE3AF3"]
                :uuid "D7D879D2-5A2D-48AA-AF8A-AADCEC228D2B"
                :creationDate #inst "2017-12-27T18:22:52.399-00:00"
                :userModificationDate #inst "2017-12-27T22:08:48.526-00:00"
                :todayIndex -503
                :startDate #inst "2017-12-27T00:00:00.000-00:00"
                :start :started
                :title "Today Todo"
                :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"})


(def inbox-today
  #:things.task{:status :open
                :uuid "29ACB795-2037-4FFD-BB09-851CDE53B4B9"
                :creationDate #inst "2017-12-27T18:22:55.210-00:00"
                :userModificationDate #inst "2017-12-27T18:24:30.732-00:00"
                :start :not-started
                :title "Inbox Todo"})


(def logbook
  #:things.task{:status :completed
                :uuid "B0DE4371-94ED-405F-82C4-8A572387C53D"
                :creationDate #inst "2017-12-27T18:23:08.159-00:00"
                :userModificationDate #inst "2017-12-27T18:23:12.417-00:00"
                :index -420
                :todayIndex -572
                :startDate #inst "2017-12-27T00:00:00.000-00:00"
                :start :started
                :stopDate #inst "2017-12-27T18:23:12.417-00:00"
                :title "Logbook"
                :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"})


(def cancelled
  #:things.task{:status :cancelled
                :uuid "01C3151D-9D0F-4A61-A0D9-F89F32AA41AB"
                :creationDate #inst "2017-12-27T18:23:15.096-00:00"
                :userModificationDate #inst "2017-12-27T18:23:19.985-00:00"
                :startDate #inst "2017-12-27T00:00:00.000-00:00"
                :start :started
                :stopDate #inst "2017-12-27T18:23:19.985-00:00"
                :title "Cancelled"
                :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"})


(def deleted-todo
  #:things.task{:status :open
                :trashed true
                :uuid "4B74DF7B-901D-40B6-A298-726CCA819100"
                :creationDate #inst "2017-12-27T18:23:24.855-00:00"
                :userModificationDate #inst "2017-12-27T18:25:26.522-00:00"
                :startDate #inst "2017-12-27T00:00:00.000-00:00"
                :start :started
                :title "Deleted Todo"
                :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"})


(def someday-todo
  #:things.task{:status :open
                :uuid "118A8E61-8DA8-46F2-BB2D-E8CC197755D9"
                :creationDate #inst "2017-12-27T18:23:31.712-00:00"
                :userModificationDate #inst "2017-12-27T18:23:38.235-00:00"
                :start :postponed
                :title "Someday Todo"})


(def next-todo
  #:things.task{:status :open
                :uuid "5C417E5A-1FFD-4E1B-9EE3-95DE5705CAEA"
                :creationDate #inst "2017-12-27T18:24:34.571-00:00"
                :userModificationDate #inst "2017-12-27T18:24:48.756-00:00"
                :startDate #inst "2045-05-13T00:00:00.000-00:00"
                :start :postponed
                :title "Next Todo"
                :todayIndexReferenceDate #inst "2045-05-13T00:00:00.000-00:00"})


(def today-project-todo
  #:things.task{:status :open
                :project [:things.project/uuid "52ADBAB5-A0EC-4D3F-BF83-2D578DAE3AF3"]
                :uuid "4C5D620C-165C-41D2-BC5B-A34065348D92"
                :creationDate #inst "2017-12-27T18:24:57.883-00:00"
                :userModificationDate #inst "2017-12-27T18:25:02.327-00:00"
                :index -506
                :start :started
                :title "Today Project Todo"})


(def todo-under-heading
  #:things.task{:status :open
                :uuid "F5AB86AA-346A-46CB-A9B4-E2C6CEC5BCF6"
                :creationDate #inst "2017-12-27T18:25:40.525-00:00"
                :userModificationDate #inst "2017-12-27T18:25:46.091-00:00"
                :start :started
                :actionGroup "ACTIONGROUP-85660FE7-37EA-4B64-8A42-2745F2CC0C93"
                :title "Todo under Heading"})


(def todo-with-checklist
  #:things.task{:status :open
                :project [:things.project/uuid "DED787E0-874A-4783-8F0F-0A02F87F8419"]
                :uuid "2ECBE4AA-2E3F-49CC-AA38-CBFFBFD2B1FD"
                :creationDate #inst "2017-12-27T18:25:50.816-00:00"
                :userModificationDate #inst "2017-12-27T18:25:56.936-00:00"
                :index -404
                :start :started
                :title "Todo with Checklist"})


(def waiting-for-todo
  #:things.task{:status :open
                :project [:things.project/uuid "DED787E0-874A-4783-8F0F-0A02F87F8419"]
                :uuid "709794DA-EB89-4A1B-BBE5-2BF8424BBA28"
                :creationDate #inst "2017-12-27T18:27:26.187-00:00"
                :userModificationDate #inst "2017-12-27T18:27:43.941-00:00"
                :index -145
                :start :started
                :title "Waiting for Todo"})


(def todo-with-due-date
  #:things.task{:status :open
                :project [:things.project/uuid "DED787E0-874A-4783-8F0F-0A02F87F8419"]
                :uuid "9CD92553-95D7-4CF2-B554-F1DE9F563018"
                :creationDate #inst "2017-12-27T22:19:57.111-00:00"
                :userModificationDate #inst "2017-12-27T22:20:09.745-00:00"
                :start :started
                :title "Due Todo"
                :dueDate #inst "2152-08-28T00:00:00.000-00:00"
                :todayIndexReferenceDate #inst "2152-08-28T00:00:00.000-00:00"})


(def repeating-todo-in-project-template
  #:things.task{:status :open
                :project [:things.project/uuid "52ADBAB5-A0EC-4D3F-BF83-2D578DAE3AF3"]
                :instanceCreationStartDate #inst "2017-12-28T00:00:00.000-00:00"
                :uuid "09A1F89A-F62D-4B45-8625-176BFA792B75"
                :creationDate #inst "2017-12-27T18:28:07.543-00:00"
                :afterCompletionReferenceDate #inst "2017-12-27T00:00:00.000-00:00"
                :userModificationDate #inst "2017-12-27T18:28:16.884-00:00"
                :repeating? true
                :nextInstanceStartDate #inst "2018-01-03T00:00:00.000-00:00"
                :start :postponed
                :title "Repeating Todo in Project"
                :todayIndexReferenceDate #inst "2018-01-03T00:00:00.000-00:00"})


(def repeating-todo-in-project
  #:things.task{:status :open
                :project [:things.project/uuid "52ADBAB5-A0EC-4D3F-BF83-2D578DAE3AF3"]
                :trashed true
                :uuid "09A1F89A-F62D-4B45-8625-176BFA792B75-20171227"
                :creationDate #inst "2017-12-26T23:00:00.000-00:00"
                :userModificationDate #inst "2017-12-27T18:28:43.829-00:00"
                :repeating? true
                :repeatingTemplate [:things.task/uuid "09A1F89A-F62D-4B45-8625-176BFA792B75"]
                :startDate #inst "2017-12-27T00:00:00.000-00:00"
                :start :started
                :title "Repeating Todo in Project"
                :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"})


(def repeating-todo-in-area-template
  #:things.task{:status :open
                :instanceCreationStartDate #inst "2017-12-28T00:00:00.000-00:00"
                :area [:things.area/uuid "C314E225-F216-4C21-B493-20ACB25CE517"]
                :uuid "FDED5B38-05E2-4C72-A106-E6D5BF72863B"
                :creationDate #inst "2017-12-27T18:28:28.092-00:00"
                :afterCompletionReferenceDate #inst "2017-12-27T00:00:00.000-00:00"
                :userModificationDate #inst "2017-12-27T18:28:30.442-00:00"
                :repeating? true
                :nextInstanceStartDate #inst "2018-01-03T00:00:00.000-00:00"
                :start :postponed
                :title "Repeating Todo in Area"
                :todayIndexReferenceDate #inst "2018-01-03T00:00:00.000-00:00"})


(def repeating-todo-in-area
  #:things.task{:status :open
                :area [:things.area/uuid "C314E225-F216-4C21-B493-20ACB25CE517"]
                :trashed true
                :uuid "FDED5B38-05E2-4C72-A106-E6D5BF72863B-20171227"
                :creationDate #inst "2017-12-26T23:00:00.000-00:00"
                :userModificationDate #inst "2017-12-27T18:28:43.825-00:00"
                :repeating? true
                :repeatingTemplate [:things.task/uuid "FDED5B38-05E2-4C72-A106-E6D5BF72863B"]
                :startDate #inst "2017-12-27T00:00:00.000-00:00"
                :start :started
                :title "Repeating Todo in Area"
                :todayIndexReferenceDate #inst "2017-12-27T00:00:00.000-00:00"})


(def checklist-done
  #:things.checklistitem{:uuid "6AB1EAF5-35DA-4873-9BB7-2ED914AB1A28"
                         :userModificationDate #inst "2017-12-27T18:26:17.316-00:00"
                         :creationDate #inst "2017-12-27T18:26:04.820-00:00"
                         :title "Checklist Done"
                         :status :completed
                         :stopDate #inst "2017-12-27T18:26:16.752-00:00"
                         :task [:things.task/uuid "2ECBE4AA-2E3F-49CC-AA38-CBFFBFD2B1FD"]})


(def checklist-item
  #:things.checklistitem{:uuid "99924D61-B2DF-4202-A724-200960375899"
                         :userModificationDate #inst "2017-12-27T18:26:04.830-00:00"
                         :creationDate #inst "2017-12-27T18:26:00.335-00:00"
                         :title "Checklist Item"
                         :status :open
                         :index -573
                         :task [:things.task/uuid "2ECBE4AA-2E3F-49CC-AA38-CBFFBFD2B1FD"]})


(def demo-data [area
                someday-project
                next-project
                today-project
                deleted-project
                project-in-area
                someday-project-heading
                heading
                today-todo
                inbox-today
                logbook
                cancelled
                deleted-todo
                someday-todo
                next-todo
                today-project
                todo-under-heading
                todo-with-checklist
                waiting-for-todo
                todo-with-due-date
                repeating-todo-in-project-template
                repeating-todo-in-area-template
                repeating-todo-in-project
                repeating-todo-in-area
                checklist-done
                checklist-item])


;; (deftest a-failing-test
;;   (is (= 1 2)))

(defonce t-snapshot (atom nil))
(deftest database-snapshot
  (pprint "running ")
  (async done
         (go-promise
          (let [snapshot (set (<?maybe (sql/snapshot (sql/open-db sql/test-demo-db))))]
            (is (= (count snapshot) (count demo-data)) )
            (is (every? snapshot demo-data))
            (done)))))