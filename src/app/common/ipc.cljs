(ns app.common.ipc
  (:require [applied-science.js-interop :as j]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise  <? <?maybe]]
            [cljs.reader :refer [read-string]]
            [taoensso.timbre :refer [info warn trace error]]))


(defn invoke
  [ipc fn-name & args]
  (go-promise
    (let [fn-name (if (keyword? fn-name) (name fn-name) fn-name)
          _ (trace (cons fn-name args))
          result  (<?maybe (j/call ipc :invoke fn-name (pr-str args)))]
      (try
        (read-string result)
        (catch :default err
          (error "could not parse edn:" result)
          err)))))


(defn- with-promise-result
  [f]
  (fn [& args]
    (new js/Promise
      (fn [resolve reject]
        (go-promise (try (let [result (<?maybe (apply f args))]
                           (trace (pr-str result))
                           (resolve (pr-str result)))
                         (catch :default e
                           (error  e)
                           (reject (pr-str e)))))))))


(defn register-ipc-handler!
  [ipc fn-name f]
  (let [fn-name (if (keyword? fn-name) (name fn-name) fn-name)]
    (j/call ipc :handle fn-name (with-promise-result
                                  (fn [event payload]
                                    (let [args (read-string payload)]
                                      (trace  (cons fn-name args))
                                      (apply f args)))))))
