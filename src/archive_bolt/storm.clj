(ns archive-bolt.storm
  (:use [backtype.storm clojure config]
        [archive-bolt.router :only [process-request]]) 
  (:gen-class))


(defbolt archive ["request" "result"]
  [tuple collector]
  (println "Archiving to bucket" bucket "with prefix" prefix)
  ;; (let []
  ;;   (emit-bolt! collector [request result] :anchor tuple)
  ;;       (ack! collector tuple))
  )      
