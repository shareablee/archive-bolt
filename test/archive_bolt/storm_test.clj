(ns archive-bolt.storm-test
  (:use clojure.test
        [backtype.storm clojure config testing]
        [amazonica.aws.s3 :only [get-object delete-object]]
        [archive-bolt.storm :only [archive]]
        [archive-bolt.fields :only [archive-input-fields
                                    archive-output-fields]])
  (:require [cheshire.core :refer :all]))


;; This doesn't do anything other than get used as a proxy for mocking
;; source tuples to the archive bolt
(defspout mock-spout archive-input-fields
  [conf context collector]
  nil)

(defn mk-test-topology
  "Returns a Storm topology for testing the archive bolt"
  []
  (topology
   {"1" (spout-spec mock-spout)}
   {"2" (bolt-spec {"1" :shuffle} archive)}))

(deftest test-archive-bolt
  "Test the topology on a local cluster"
  (with-simulated-time-local-cluster [cluster]
    (let [content (generate-string {:yo "dawg"})
          key "collection/twitter/1.0/test_user_id/test.json"
          ;; This becomes the input to the archive bolt
          mock-sources {"1" [["s3" key content]]}
          topo (mk-test-topology)
          bucket-name "dev.shareablee.com"
          conf {"AWS_ACCESS_KEY_ID" "AKIAIPOBJD5JETWYK7TA"
                "AWS_SECRET_ACCESS_KEY" "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"
                "AWS_S3_REGION" "us-east-1"
                "S3_BUCKET" "dev.shareablee.com"}
          creds {:access-key "AKIAIPOBJD5JETWYK7TA"
                 :secret-key "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"} 
          results (complete-topology cluster
                                     topo
                                     :storm-conf conf
                                     :mock-sources mock-sources)
          control (get-object creds bucket-name key)
          control-content (-> control :input-stream slurp)]
      ;; Verify that the side effect of writing to s3 worked
      (is (= content control-content))
      ;; Clean up
      (println "Deleting test key")
      (delete-object creds bucket-name key)
      ;; Check the output of the bolt matches expected tuple output      
      ;; Order is not guaranteed so we are using the built in storm
      ;; equality function ms= rather than =
      (is (ms= [[(str "s3://" bucket-name
                      "/collection/twitter/1.0/test_user_id/test.json")]]
               (read-tuples results "2"))))))
