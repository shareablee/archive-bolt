(ns archive-bolt.backends.s3-test
  (:use clojure.test
        [amazonica.aws.s3 :only [get-object delete-object]]
        archive-bolt.backends.s3
        amazonica.aws.s3transfer)
  (:require [cheshire.core :refer :all]))


(deftest test-store
  (let [key "collection/twitter/1.0/test_user_id/test.json"
        bucket-name "dev.shareablee.com"
        conf {"AWS_ACCESS_KEY_ID" "AKIAIPOBJD5JETWYK7TA"
              "AWS_SECRET_ACCESS_KEY" "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"
              "AWS_S3_REGION" "us-east-1"
              "S3_BUCKET" "dev.shareablee.com"}
        creds {:access-key (get conf "AWS_ACCESS_KEY_ID")
               :secret-key (get conf "AWS_SECRET_ACCESS_KEY")
               :endpoint   (get conf "AWS_S3_REGION")}
        content (generate-string {:yo "dawg"})
        result (store conf key content)
        control (get-object creds bucket-name key)
        control-content (-> control :input-stream slurp)]
    (is (= content control-content))
    (println "Deleting test s3 key")
    (delete-object creds bucket-name key)))
