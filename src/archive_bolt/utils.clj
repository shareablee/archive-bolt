(ns archive-bolt.utils)

(defn get-or-throw [m k]
  (if-let [result (get m k)]
    result
    (throw (Exception. (format "Key %s was not found in properties: %s" k m)))))
