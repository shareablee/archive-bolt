(defproject com.shareablee/archive-bolt "0.1.10"
  :description "Reusable storm bolt for storing data to S3"
  :url "https://github.com/shareablee/archive-bolt"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :dependencies [[amazonica "0.2.3"]
                 [cheshire "5.2.0"]
                 [clj-time "0.4.4"]]
  :profiles
  {:dev
   {:dependencies [[org.apache.storm/storm-core "0.9.3"]
                   [org.clojure/clojure "1.5.1"]
                   [org.clojure/tools.nrepl "0.2.2"]]}}
  :repositories
  [["releases"
    {:url "s3p://shareablee-jar-repo/releases"
     :username :env/shareablee_aws_access_key
     :passphrase :env/shareablee_aws_secret_access_key
     :sign-releases false
     :snapshots false}]
   ["snapshots"
    {:url "s3p://shareablee-jar-repo/snapshots"
     :username :env/shareablee_aws_access_key
     :passphrase :env/shareablee_aws_secret_access_key}]])
