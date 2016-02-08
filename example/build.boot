(set-env!
 :source-paths   #{"src" "react-support"}
 ;:target-path "app/build"
 :exclusions ['cljsjs/react]
 :dependencies '[
                 [mattsum/boot-react-native "0.1.1-SNAPSHOT" :scope "test"]
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
                 [reagent                        "0.5.1"]
                 ]
 )

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

(deftask dev
  "Build app and watch for changes"
  [p platform PLATFORM kw "The platform to target (ios or android)"]
  []
  (assert (or (nil? platform) (#{:ios :android} platform)))
  (comp (watch)
        (if (= :ios platform) (rn/run-in-simulator) identity)
        (reload :on-jsload 'mattsum.simple-example.core/on-js-reload
                :port 8079
                :ws-host "localhost"
                )
        (rn/before-cljsbuild)

        (cljs-repl :ws-host "localhost"
                   :port 9001
                   :ip "0.0.0.0")

        (cljs :main "mattsum.simple-example.core")
        (rn/after-cljsbuild :server-url "localhost:8081")
        (if (= :ios platform) (rn/print-ios-log) identity)
        (if (= :android platform) (rn/print-android-log) identity)
        (target :dir ["app/build"])
        ))

(deftask packager
  []
  (watch)
  (rn/start-rn-packager))
