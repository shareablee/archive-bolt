(ns archive-bolt.backends.s3
  (:use [amazonica.aws.s3 :only [put-object]]
        [taoensso.timbre :as timbre :only (info warn error fatal)]
        amazonica.aws.s3transfer))


(def creds {:access-key "AKIAIUBLJXHPDXHG5PFQ"
            :secret-key "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"
            :endpoint   "us-east-1"})

(def bucket-name "stage.shareablee.com")

(defn safe-put
  "Attempt to PUT the file to s3 returns full s3 path when successful or 
   nil if unsuccessful. Retries on failure up to max-retries times."
  [creds bucket location file
   & {:keys [retry-count max-retries wait-time]
      :or {retry-count 0, max-retries 10 wait-time 1000}}]
  (try (do (put-object creds :bucket-name bucket :key location :file file)
           (str "s3://" bucket "/" location)) 
       (catch Exception e (do (Thread/sleep wait-time)
                              (info "safe-put retry attempt" retry-count)
                              (if (< retry-count max-retries)
                                (safe-put creds bucket location file
                                          :retry-count (inc retry-count)
                                          :max-retries max-retries
                                          :wait-time wait-time)
                                (error "safe-put failed to store to s3"))))))

(defn store
  "Write serialized content to the specified location in s3"
  [location content]
  (let [escaped-location (clojure.string/replace location "/" "_")
        tmp-path (str "/tmp/" escaped-location)
        _ (spit tmp-path content)
        file (clojure.java.io/file tmp-path)
        result (safe-put creds bucket-name location file)]
    (clojure.java.io/delete-file tmp-path)
    result))
