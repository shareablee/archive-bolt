(ns archive-bolt.backends.s3
  (:require [backtype.storm.log :as storm] 
            [amazonica.aws.s3 :as s3]
            [archive-bolt.utils :refer [get-or-throw]]
            [cheshire.core :as json]
            [clojure.string :as st]))


(defn mk-credentials
  [conf]
  {:access-key (get-or-throw conf "AWS_ACCESS_KEY_ID")
   :secret-key (get-or-throw conf "AWS_SECRET_ACCESS_KEY")
   :endpoint   (get-or-throw conf "AWS_S3_REGION")})

(defn safe-put
  "Attempt to PUT the file to s3 returns full s3 path when successful or 
   nil if unsuccessful. Retries on failure up to max-retries times."
  [creds bucket location file
   & {:keys [retry-count max-retries wait-time]
      :or {retry-count 0, max-retries 10 wait-time 1000}}]
  ;; Store the content and return the location
  (try (do (s3/put-object creds :bucket-name bucket :key location :file file)
           (str "s3://" bucket "/" location)) 
       (catch Exception e
         (do (Thread/sleep wait-time)
             (storm/log-error e " Failed to store in s3. "
                              "Retry count: " retry-count)
             (if (< retry-count max-retries)
               (safe-put creds bucket location file
                         :retry-count (inc retry-count)
                         :max-retries max-retries
                         :wait-time wait-time)
               (storm/log-warn "safe-put failed to store to s3 after "
                               max-retries " attempts for " bucket location))))))

(defn store-content
  [conf location content]
  (let [;; For backwards compatibility look for the old key as fallback
        bucket (or (get conf "ARCHIVE_WRITE_S3_BUCKET")
                   (get conf "S3_BUCKET"))
        _ (when-not bucket
            (throw (Exception. "Missing config field ARCHIVE_WRITE_S3_BUCKET")))
        creds (mk-credentials conf)
        escaped-location (clojure.string/replace location "/" "_")
        tmp-file (java.io.File/createTempFile "archive_" escaped-location)
        tmp-path (.getAbsolutePath tmp-file)
        _ (spit tmp-path content)
        file (clojure.java.io/file tmp-path)
        result (safe-put creds bucket location file)]
    (clojure.java.io/delete-file tmp-path)
    result))

(defn get-keys-from-results [result-hm]
  (map :key (:object-summaries result-hm)))

(defn lookup-key
  "Lookup the given key and return nil if it does not exist or fails.
   Returns a hashmap of archive meta data and the deserialized json value
   for the given key"
  [credentials bucket-name location key]
  {:meta {:location location
          :full-path key
          :file-name (last (st/split key #"/"))}
   :value (try (-> (s3/get-object credentials
                                  :bucket-name bucket-name
                                  :key key)
                   :input-stream 
                   slurp
                   (json/parse-string true))
               (catch Exception e
                 (storm/log-warn
                  (format "Failed to get bucket: %s, key: %s, error: %s "
                          bucket-name key e))))})

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

#_(defn parallel-lookup
  [s3-keys parallelism creds bucket-name location]
  (storm/log-message "Looking up " (count s3-keys) " keys.")
  (let [[values elapsed-ms]
        (capture-time
          (mapcat (fn [ks]
                    ;; this should really be a buffer that launches new conns as
                    ;; some results complete
                    (pmap #(lookup-key creds bucket-name location %) ks))
                  (partition-all parallelism s3-keys)))]
    (storm/log-message "Lookup took " elapsed-ms "ms")
    values))

(defn parallel-lookup*
  "Looks up keys with specified parallelism."
  [s3-keys parallelism creds bucket-name location]
  (loop [accum []
         active (map #(future (lookup-key creds bucket-name location %))
                     (take parallelism s3-keys))
         queued (drop parallelism s3-keys)]
    (Thread/sleep 100)
    (let [[done in-progress] ((juxt #(get % true) #(get % false))
                               (group-by realized? active))
          accum (concat accum (remove nil? (map deref done)))
          new-requests (map #(future (lookup-key creds bucket-name location %))
                            (take (count done) queued))
          active (concat in-progress new-requests)
          queued (drop (count done) queued)]
      (if (seq active)
        (recur accum active queued)
        accum))))

(defn parallel-lookup
  [s3-keys parallelism creds bucket-name location]
  (storm/log-message "Looking up " (count s3-keys) " keys, "
                     parallelism " at a time.")
  (let [[values elapsed-ms]
        (capture-time
          (parallel-lookup* s3-keys parallelism creds bucket-name location))]
    (storm/log-message "Looked up " (count s3-keys) " keys in " elapsed-ms "ms")
    values))

(defn filter-from-backend
  "Search s3 for keys at the given location. Take the list of keys and look
   them up. If the results are paginated, recur until all results are returned.
   Results are paginated by 1,000 keys as per the S3 API docs. Results keys are 
   filtered by filter-fn. Returns a collection of results."
  [conf location & [filter-fn accum marker]]
  (let [creds (mk-credentials conf)
        ;; For backwards compatibility look for the old key as fallback
        bucket-name (or (get conf "ARCHIVE_READ_S3_BUCKET")
                        (get conf "S3_BUCKET")
                        (throw (Exception. "Missing config field ARCHIVE_READ_S3_BUCKET")))
        ;; Search s3 for all keys at the location
        search-results (s3/list-objects creds
                                        :bucket-name bucket-name
                                        :prefix location
                                        :marker marker)
        ;; Grab the keys and optionally filter them
        s3-keys ((or filter-fn identity) (get-keys-from-results search-results))
        values (parallel-lookup s3-keys 50 creds bucket-name location)
        result (concat accum values)]
    ;; If there is a next marker then we should recur
    (if-let [next-marker (:next-marker search-results)] 
      ;; NOTE optional args must be in a vector when using recur
      (do (storm/log-message "Paging archive results at " location)
          (recur conf location [filter-fn result next-marker]))
      result)))
