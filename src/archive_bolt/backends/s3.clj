(ns archive-bolt.backends.s3
  (:use [amazonica.aws.s3 :only [put-object]]
        backtype.storm.log
        amazonica.aws.s3transfer))


(defn safe-put
  "Attempt to PUT the file to s3 returns full s3 path when successful or 
   nil if unsuccessful. Retries on failure up to max-retries times."
  [creds bucket location file
   & {:keys [retry-count max-retries wait-time]
      :or {retry-count 0, max-retries 10 wait-time 1000}}]
  ;; Store the content and return the location
  (try (do (put-object creds :bucket-name bucket :key location :file file)
           (str "s3://" bucket "/" location)) 
       (catch Exception e (do (Thread/sleep wait-time)
                              (log-error e)
                              (log-message "safe-put retry attempt " retry-count)
                              (if (< retry-count max-retries)
                                (safe-put creds bucket location file
                                          :retry-count (inc retry-count)
                                          :max-retries max-retries
                                          :wait-time wait-time)
                                (log-warn "safe-put failed to store to s3"))))))

(defn store
  "Write serialized content to the specified location in s3.
   Conf arg is a storm Config instance."
  [conf location content]
  (let [bucket (get conf "S3_BUCKET")
        creds {:access-key (get conf "AWS_ACCESS_KEY_ID")
               :secret-key (get conf "AWS_SECRET_ACCESS_KEY")
               :endpoint   (get conf "AWS_S3_REGION")}
        escaped-location (clojure.string/replace location "/" "_")
        tmp-file (java.io.File/createTempFile "archive_" escaped-location)
        tmp-path (.getAbsolutePath tmp-file)
        _ (spit tmp-path content)
        file (clojure.java.io/file tmp-path)
        result (safe-put creds bucket location file)]
    (clojure.java.io/delete-file tmp-path)
    result))
