(ns archive-bolt.storm
  (:use [backtype.storm clojure config]
        [archive-bolt.router :only [process-request]]
        [taoensso.timbre :as timbre :only (info warn error fatal)]) 
  (:gen-class))


;; Takes a backend, location, and content and emits the location to
;; the file archived
(defbolt archive ["result"]
  [tuple collector]
  (let [{:keys [backend location content]} tuple
        _ (info "archive args"
                :backend backend
                :location location
                :content content)
        result (process-request backend location content)]
    ;; If we don't get a result from storage we need to fail the tuple
    (if result
      (do (emit-bolt! collector [result] :anchor tuple)
          (ack! collector tuple))
      (fail! collector tuple))))
