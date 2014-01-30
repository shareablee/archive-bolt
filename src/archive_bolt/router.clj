(ns archive-bolt.router
  (:require [archive-bolt.backends.s3 :as s3]))

;; 

(def handlers
  ;; List backend handler functions here keyed off of the backend name
  {"s3" s3/store})

(defn valid? [])

(defn process-request
  "Route the request to the correct storage handler. Conf is a storm 
   Config instance."
  [conf backend location content]
  (let [handler (get handlers backend)]
    (handler conf location content)))

