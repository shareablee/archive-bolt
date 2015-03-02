(defproject com.shareablee/archive-bolt "0.1.7"
  :description "Reusable storm bolt for storing data to S3"
  :url "https://github.com/shareablee/archive-bolt"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-time "0.4.4"]                 
                 [cheshire "5.2.0"]
                 [amazonica "0.2.3"]
                 [org.apache.httpcomponents/httpclient "4.2.5"]]
  :profiles {:dev {:dependencies [[org.apache.storm/storm-core "0.9.3"]
                                  [org.clojure/tools.nrepl "0.2.2"]]}})
