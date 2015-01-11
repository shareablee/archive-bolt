(ns archive-bolt.backends.s3-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [amazonica.aws.s3 :as s3]
            [archive-bolt.backends.core :as backend]
            [archive-bolt.backends.s3 :as s3-backend]))


(def test-location "testing/location/")

(def test-file-name "test.json")

(def test-key (str test-location test-file-name))

(defn get-test-conf []
  (let [required ["AWS_ACCESS_KEY_ID"
                  "AWS_SECRET_ACCESS_KEY"
                  "AWS_S3_REGION"
                  "S3_BUCKET"]
        get-env-var #(or (System/getenv %)
                         (throw (Exception.
                                 (str "Missing required test environment variable: " %))))]
    (reduce #(assoc %1 %2 (get-env-var %2)) {} required)))

(defn mk-test-creds []
  (let [test-conf (get-test-conf)]
    {:access-key (get test-conf "AWS_ACCESS_KEY_ID")
     :secret-key (get test-conf "AWS_SECRET_ACCESS_KEY")
     :endpoint   (get test-conf "AWS_S3_REGION")}))

(def test-content {:yo "dawg"})

(def test-content-str (json/generate-string test-content))

(defn with-s3-key [f]
  (let [test-conf (get-test-conf)
        test-creds (mk-test-creds)
        test-bucket-name (get test-conf "S3_BUCKET")]
    [f]
    (backend/store :s3 test-conf test-key test-content-str)
    (f)
    (s3/delete-object test-creds test-bucket-name test-key)))

(deftest test-store
  (let [test-conf (get-test-conf)
        test-bucket-name (get test-conf "S3_BUCKET")
        result (backend/store :s3 test-conf test-key test-content-str)
        test-creds (mk-test-creds)
        expected (s3/get-object test-creds test-bucket-name test-key)
        expected-content (-> expected :input-stream slurp)]
    (is (= test-content-str expected-content))))

(deftest test-filter-from-backend
  (with-s3-key
    #(is (= (backend/filter-from-backend :s3 (get-test-conf) test-location)
            [{:meta {:location test-location
                    :full-path test-key
                    :file-name test-file-name}
              :value test-content}]))))

(deftest test-filter-from-backend-no-results)
  #(is (= (backend/filter-from-backend :s3 (get-test-conf) test-location)
          []))

(deftest test-filter-from-backend-with-filter-fn
  (with-redefs [s3/list-objects (fn [& args]
                                  {:object-summaries [{:key "foo"} {:key "foo"}]
                                   :next-marker nil})
                s3-backend/lookup-key (fn [_ _ _ k] {k k})]
    (is (= (backend/filter-from-backend :s3
                                        (get-test-conf)
                                        test-location
                                        (fn [coll] (set coll)))
           [{"foo" "foo"}]))))

(defn mock-list-objects
  [_ & {:keys [bucket-name prefix marker]}]
  (if (= marker 123)
    {:object-summaries [{:key "foo"}] :next-marker nil}
    {:object-summaries [{:key "bar"}] :next-marker 123}))

(deftest test-filter-from-backend-pagination
  "Test paging through results of searching s3 without actually hitting s3"
  (with-redefs [s3/list-objects mock-list-objects
                s3-backend/lookup-key (fn [_ _ _ k] {k k})]
    (is (= (backend/filter-from-backend :s3 (get-test-conf) test-location)
           [{"bar" "bar"} {"foo" "foo"}]))))
