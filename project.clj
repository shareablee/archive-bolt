(defproject archive-bolt "0.1.0-SNAPSHOT"
  :description "Archive data to a storage backend"
  :url "http://www.shareablee.com"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-time "0.4.4"]                 
                 [cheshire "5.2.0"]
                 [amazonica "0.2.3"]
                 [org.apache.httpcomponents/httpclient "4.2.5"]]
  :profiles {:dev {:dependencies [[storm "0.9.0.1"]
                                  [org.clojure/tools.nrepl "0.2.2"]]}})
