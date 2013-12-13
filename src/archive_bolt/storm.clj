(ns archive-bolt.storm
  (:use [backtype.storm clojure config]) 
  (:gen-class))


(defbolt archive ["request" "result"]
  [tuple collector]
  (println "ARCHIVED")
  ;; (let []
  ;;   (emit-bolt! collector [request result] :anchor tuple)
  ;;       (ack! collector tuple))
  )      
