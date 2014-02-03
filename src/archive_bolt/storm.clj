(ns archive-bolt.storm
  (:use [backtype.storm clojure config log]
        [archive-bolt.router :only [process-request]]
        [archive-bolt.fields :only [archive-output-fields]]) 
  (:gen-class))


;; Takes a backend, location, and content and emits the location to
;; the file archived. When using the s3 backend the bucket to archive
;; is determined by the configuration property S3_BUCKET
(defbolt archive archive-output-fields {:prepare true}
  [conf context collector]
  (bolt
   (execute
    [tuple]
    (let [{:keys [backend location content]} tuple
          result (process-request conf backend location content)]
      ;; If we don't get a result from storage we need to fail the tuple
      (if result
        (do (emit-bolt! collector [result] :anchor tuple)
            (ack! collector tuple))
        (do (log-warn "No result returned from backend")
            (fail! collector tuple)))))))
