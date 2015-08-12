(defproject com.shareablee/archive-bolt "0.1.7-youtube-SNAPSHOT"
  :description "Reusable storm bolt for storing data to S3"
  :url "https://github.com/shareablee/archive-bolt"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-time "0.4.4"]                 
                 [cheshire "5.2.0"]
                 [amazonica "0.2.3"]
                 [org.apache.httpcomponents/httpclient "4.2.5"]]
  :profiles {:dev {:dependencies [[storm "0.9.0.1"]
                                  [org.clojure/tools.nrepl "0.2.2"]]}}
  :plugins
  [[s3-wagon-private "1.1.2"]]
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
