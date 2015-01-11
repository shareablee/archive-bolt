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
       (catch Exception e (do (Thread/sleep wait-time)
                              (storm/log-error e)
                              (storm/log-message "safe-put retry attempt " retry-count)
                              (if (< retry-count max-retries)
                                (safe-put creds bucket location file
                                          :retry-count (inc retry-count)
                                          :max-retries max-retries
                                          :wait-time wait-time)
                                (storm/log-warn "safe-put failed to store to s3"))))))

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
        keys ((or filter-fn identity) (get-keys-from-results search-results))
        values (pmap #(lookup-key creds bucket-name location %) keys)
        result (concat accum values)]
    ;; If there is a next marker then we should recur
    (if-let [next-marker (:next-marker search-results)] 
      ;; NOTE optional args must be in a vector when using recur
      (do (storm/log-message "Paging archive results at " location)
          (recur conf location [filter-fn result next-marker]))
      result)))
