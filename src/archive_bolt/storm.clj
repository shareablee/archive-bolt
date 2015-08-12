(ns archive-bolt.storm
  (:require [backtype.storm.clojure :refer [defbolt bolt emit-bolt! ack! fail!]]
            [backtype.storm.log :refer [log-debug log-warn log-message]]
            [archive-bolt.backends.core :refer [store filter-from-backend]]
            [archive-bolt.fields :as fields]) 
  (:gen-class))


;; Takes a backend, location, and content and emits the location to
;; the file archived. When using the s3 backend the bucket to archive
;; is determined by the configuration property S3_BUCKET
(defbolt archive fields/archive-output-fields
  {:prepare true}
  [conf context collector]
  (bolt
   (execute
    [tuple]
    (let [{:keys [meta backend location content]} tuple
          result (store backend conf location content)]
      ;; If we don't get a result from storage we need to fail the tuple
      (if result
        (do (emit-bolt! collector [meta result] :anchor tuple)
            (ack! collector tuple))
        (do (log-warn "No result returned from backend. Save failed, failing...")
            (fail! collector tuple)))))))

(defn -archive-read
  "Read from the archive. Optionally pass in a filter function
   to filter search results from the given location.

   Acks the tuple even if there are no results."
  [conf collector tuple & [filter-fn]]
  (let [{:keys [meta backend location]} tuple
        results (filter-from-backend backend conf location filter-fn)]
    (if (seq results)
      (emit-bolt! collector [meta results] :anchor tuple)
      (log-debug (format "No results returned from %s backend at %s"
                         backend location)))
    (ack! collector tuple)))

(defbolt archive-read fields/archive-read-output-fields
  {:prepare true}
  [conf context collector]
  (bolt
    (execute
      [tuple]
      (-archive-read conf collector tuple))))

;; TEMP HACK for youtube replay
(defn key->object-id
  [k]
  (let [file-name (last (clojure.string/split k #"\/"))
        file-name-no-ext (first (clojure.string/split file-name #"\."))
        splits (clojure.string/split file-name-no-ext #"_")]
    (apply str (butlast splits))))
(defn youtube-filter-fn
  [ks]
  ;; NOTE: relies on lexicographical ordering guarantee from S3,
  ;; http://stackoverflow.com/questions/4102115/does-the-listbucket-command-guarantee-the-results-are-sorted-by-key
  (mapv last (partition-by key->object-id ks)))

(defbolt archive-read-filtered fields/archive-read-output-fields
  {:prepare true :params [filter-fn]}
  [conf context collector]
  (bolt
    (execute
      [tuple]
      ;; HACK to pass in a function as a param to a storm bolt it must be
      ;; quoted and then resolved or you will have a class not found
      ;; exception thrown at run time
      (if-let [;filter-fn-resolved (resolve filter-fn)
               ;; TEMP HACK for youtube
               filter-fn-resolved youtube-filter-fn]
        (do
          (-archive-read conf collector tuple filter-fn-resolved))
        (do
          (log-warn "Could not resolve filter-fn: " filter-fn
                    ". Using clojure fn `identity` instead")
          (-archive-read conf collector tuple identity))))))
