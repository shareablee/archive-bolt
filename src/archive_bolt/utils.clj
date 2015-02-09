(ns archive-bolt.utils)

(defn get-or-throw [m k]
  (if-let [result (get m k)]
    result
    (throw (Exception. (format "Key %s was not found in properties: %s" k m)))))

(defn capture-time-fn
  "Returns a tuple of how much time it took (in ms) for the thunk to
  execute, and the return value of the function."
  [f]
  (let [start (System/currentTimeMillis)]
    [(f) (/ (double (- (System/currentTimeMillis) start)) 1000.0)]))

(defmacro capture-time
  "Returns a tuple of how much time it took (in ms) the code to
  execute and the value of the last expression."
  [& body]
  `(capture-time-fn (fn [] ~@body)))