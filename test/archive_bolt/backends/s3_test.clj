(ns archive-bolt.backends.s3-test
  (:use clojure.test
        [amazonica.aws.s3 :only [get-object delete-object]]
        archive-bolt.backends.s3
        amazonica.aws.s3transfer)
  (:require [cheshire.core :refer :all]))


(deftest test-store
  (let [key "collection/twitter/1.0/test_user_id/test.json"
        content (generate-string {:yo "dawg"})
        result (store key content)
        control (get-object creds bucket-name key)
        control-content (-> control :input-stream slurp)]
    (is (= content control-content))
    (println "Deleting test s3 key")
    (delete-object creds bucket-name key)))
