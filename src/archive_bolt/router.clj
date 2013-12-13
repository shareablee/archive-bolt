(ns archive-bolt.router
  (:require [archive-bolt.backends.s3 :as s3]))

(def handlers
  ;; List backend handler functions here keyed off of the backend name
  {:s3 s3/store})

(defn process-request
  "Route the request to the correct storage handler"
  [task]
  (let [{:keys [backend]} task
        handler (get handlers backend)]
    (handler task)))

