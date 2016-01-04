(set-env!
 :source-paths   #{"src"}
 :target-path "app/build"
 :dependencies '[
                 [mattsum/boot-react-native "0.1.0-SNAPSHOT" :scope "test"]
                 [adzerk/boot-cljs               "1.7.170-3"       :scope  "test"]
                 [adzerk/boot-cljs-repl          "0.3.0"           :scope  "test"]
                 [adzerk/boot-reload             "0.4.2"           :scope  "test"]
                 [pandeiro/boot-http             "0.7.1-SNAPSHOT"  :scope  "test"]
                 [crisptrutski/boot-cljs-test    "0.2.1-SNAPSHOT"  :scope  "test"]
                 [com.cemerick/piggieback        "0.2.1"           :scope  "test"]
                 [weasel                         "0.7.0"           :scope  "test"]
                 [org.clojure/tools.nrepl        "0.2.12"          :scope  "test"]
                 [org.clojure/clojure            "1.7.0"]
                 [org.clojure/clojurescript      "1.7.170"]
                 ]
 :mirrors {#"clojars" {:name "mr1"
                       :url "https://clojars-mirror.tcrawley.org/repo/"}})

(require
 '[adzerk.boot-cljs             :refer  [cljs]]
 '[adzerk.boot-cljs-repl        :refer  [cljs-repl  start-repl]]
 '[adzerk.boot-reload           :refer  [reload]]
 '[crisptrutski.boot-cljs-test  :as test :refer  [test-cljs]]
 '[pandeiro.boot-http           :refer  [serve]]
 '[boot.core                    :as     b]
 '[clojure.string               :as     s]
 '[mattsum.boot-react-native    :as     rn]
 )

(deftask fast-build []
  (comp (serve :handler 'mattsum.simple-log-server/log
            :port 8000)
     (watch)
     (reload :on-jsload 'mattsum.simple-example.core/on-js-reload
             :port 8079
             :ws-host "matt-dev"
             )
     (rn/before-cljsbuild)

     (cljs-repl :ws-host "matt-dev"
                :ip "0.0.0.0")

     (cljs :main "mattsum.simple-example.core")
     (rn/after-cljsbuild :server-url "matt-dev:8081")
     ))

(deftask packager
  []
  (watch)
  (rn/start-rn-packager))
