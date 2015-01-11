(ns archive-bolt.storm-test
  (:require [clojure.test :refer :all] 
            [backtype.storm.clojure :refer :all]
            [backtype.storm.testing :refer :all]
            [amazonica.aws.s3 :refer [get-object delete-object]]
            [archive-bolt.storm :refer [archive archive-read archive-read-filtered]]
            [archive-bolt.fields :refer [archive-input-fields
                                         archive-read-input-fields
                                         archive-output-fields]]
            [archive-bolt.backends.s3-test :refer :all]
            [cheshire.core :refer :all]))


;; This doesn't do anything other than get used as a proxy for mocking
;; source tuples to the archive bolt
(defspout mock-write-spout archive-input-fields
  [conf context collector]
  nil)

(defspout mock-read-spout archive-read-input-fields
  [conf context collector]
  nil)

(defn mk-test-write-topology
  "Returns a Storm topology for testing the archive bolt"
  []
  (topology
   {"1" (spout-spec mock-write-spout)}
   {"2" (bolt-spec {"1" :shuffle} archive)}))

(defn mk-test-read-topology
  "Returns a Storm topology for testing the archive bolt"
  []
  (topology
   {"1" (spout-spec mock-read-spout)}
   {"2" (bolt-spec {"1" :shuffle} archive-read)}))

(defn mk-test-read-filtered-topology
  "Returns a Storm topology for testing the archive bolt"
  []
  (topology
   {"1" (spout-spec mock-read-spout)}
   {"2" (bolt-spec {"1" :shuffle} (archive-read-filtered `test-filter-fn))}))

(deftest test-archive-write-bolt
  "Test the topology on a local cluster"
  (with-simulated-time-local-cluster [cluster]
    (let [test-conf (get-test-conf)
          test-creds (mk-test-creds)
          test-bucket-name (get test-conf "S3_BUCKET")
          ;; This becomes the input to the archive bolt
          mock-sources {"1" [[{} "s3" test-location test-content-str]]}
          topo (mk-test-write-topology)
          results (complete-topology cluster
                                     topo
                                     :storm-conf test-conf
                                     :mock-sources mock-sources)
          expected (get-object test-creds test-bucket-name test-location)
          expected-content (-> expected :input-stream slurp)]
      ;; Clean up
      (delete-object test-creds test-bucket-name test-location)      
      ;; Verify that the side effect of writing to s3 worked
      (is (= test-content-str expected-content))
      ;; Check the output of the bolt matches expected tuple output      
      ;; Order is not guaranteed so we are using the built in storm
      ;; equality function ms= rather than =
      (is (ms= [[{} (str "s3://" test-bucket-name "/" test-location)]]
               (read-tuples results "2"))))))

(deftest test-archive-read-bolt
  (with-s3-key
    #(with-simulated-time-local-cluster [cluster]
       (let [test-conf (get-test-conf)
             ;; This becomes the input to the archive bolt
             mock-sources {"1" [[{} "s3" test-location]]}
             topo (mk-test-read-topology)
             bucket-name "dev.shareablee.com"
             results (complete-topology cluster
                                        topo
                                        :storm-conf test-conf
                                        :mock-sources mock-sources)
             expected [{:meta {:location test-location
                               :full-path test-key
                               :file-name test-file-name}
                        :value test-content}]]
         ;; Check the output of the bolt matches expected tuple output      
         ;; Order is not guaranteed so we are using the built in storm
         ;; equality function ms= rather than =
         (is (ms= [[{} expected]]
                  (read-tuples results "2")))))))

(deftest test-archive-read-filtered-bolt
  (with-s3-key
    #(with-simulated-time-local-cluster [cluster]
       (let [test-conf (get-test-conf)
             mock-sources {"1" [[{} "s3" test-location]]}
             topo (mk-test-read-filtered-topology)
             bucket-name (get test-conf "S3_BUCKET")
             results (complete-topology cluster
                                        topo
                                        :storm-conf test-conf
                                        :mock-sources mock-sources)
             expected [{:meta {:location test-location
                               :full-path test-key
                               :file-name test-file-name}
                        :value test-content}]]
         ;; Check the output of the bolt matches expected tuple output      
         ;; Order is not guaranteed so we are using the built in storm
         ;; equality function ms= rather than =
         (is (ms= [[{} expected]]
                  (read-tuples results "2")))))))
