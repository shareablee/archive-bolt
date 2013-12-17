(ns archive-bolt.storm-test
  (:use clojure.test
        [archive-bolt.backends.s3 :only [bucket-name creds]]
        [backtype.storm clojure config testing]
        [amazonica.aws.s3 :only [get-object delete-object]]
        [archive-bolt.storm :only [archive]])
  (:require [cheshire.core :refer :all]))


(def api-archive-fields
  ["backend" "location" "content"])

;; This doesn't do anything other than get used as a proxy for mocking
;; source tuples to the archive bolt
(defspout mock-spout api-archive-fields
  [conf context collector]
  nil)

(defn mk-test-topology
  "Returns a Storm topology for testing the archive bolt"
  []
  (topology
   {"1" (spout-spec mock-spout)}
   {"2" (bolt-spec {"1" api-archive-fields} archive)}))

(deftest test-archive-bolt
  "Test the topology on a local cluster"
  (with-simulated-time-local-cluster [cluster]
    (let [content (generate-string {:yo "dawg"})
          key "collection/twitter/1.0/test_user_id/test.json"
          ;; This becomes the input to the archive bolt
          mock-sources {"1" [["s3" key content]]}
          topo (mk-test-topology)
          results (complete-topology cluster
                                     topo
                                     :mock-sources mock-sources)
          control (get-object creds bucket-name key)
          control-content (-> control :input-stream slurp)]
      (println "RESULTS" (read-tuples results "1"))
      ;; Verify that the side effect of writing to s3 worked
      (is (= content control-content))
      ;; Clean up
      (println "Deleting test key")
      (delete-object creds bucket-name key)
      ;; Check the output of the bolt matches expected tuple output
      (is (ms= [[control-content]]
               (read-tuples results "1"))))))
