(ns archive-bolt.backends.s3
  (:use [amazonica.aws.s3 :only [put-object]]
        amazonica.aws.s3transfer))


(def creds {:access-key "AKIAIUBLJXHPDXHG5PFQ"
            :secret-key "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"
            :endpoint   "us-east-1"})

(def bucket-name "stage.shareablee.com")

(defn gen-file-name [])

(defn safe-put [creds bucket location file]
  (try (put-object creds :bucket-name bucket :key location :file file)
       (catch Exception e (println e))))

(defn store
  "Write data to the specified location in s3"
  [location content]
  (let [escaped-location (clojure.string/replace location "/" "_")
        tmp-path (str "/tmp/" escaped-location)
        _ (spit tmp-path content)
        file (clojure.java.io/file tmp-path)
        result (safe-put creds bucket-name location file)]
    (clojure.java.io/delete-file tmp-path)
    result))
