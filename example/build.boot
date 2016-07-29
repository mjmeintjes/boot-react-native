(set-env!
 :source-paths   #{"src" "react-support"}
 :resource-paths   #{"resources"}
 :exclusions ['cljsjs/react]
 :dependencies '[
                 [org.clojars.pesterhazy/boot-react-native      "0.3-SNAPSHOT" :scope "test"]
                 [adzerk/boot-cljs               "1.7.228-1"       :scope  "test"]
                 [adzerk/boot-cljs-repl          "0.3.0"           :scope  "test"]
                 [adzerk/boot-reload             "0.4.2"           :scope  "test"]
                 [pandeiro/boot-http             "0.7.1-SNAPSHOT"  :scope  "test"]
                 [crisptrutski/boot-cljs-test    "0.2.1-SNAPSHOT"  :scope  "test"]
                 [com.cemerick/piggieback        "0.2.1"           :scope  "test"]
                 [weasel                         "0.7.0"           :scope  "test"]
                 [org.clojure/tools.nrepl        "0.2.12"          :scope  "test"]
                 [org.clojure/clojure            "1.7.0"]
                 [org.clojure/clojurescript      "1.7.170"]
                 [reagent                        "0.6.0-rc"]
                 ;; [react-native-externs "0.0.1-SNAPSHOT"]
                 ]
 )

(require
 '[adzerk.boot-cljs             :refer  [cljs]]
 '[adzerk.boot-cljs-repl        :refer  [cljs-repl  start-repl]]
 '[adzerk.boot-reload           :refer  [reload]]
 '[crisptrutski.boot-cljs-test  :as test :refer  [test-cljs]]
 '[pandeiro.boot-http           :refer  [serve]]
 '[boot.core                    :as     b]
 '[boot.util                    :as     u]
 '[clojure.string               :as     s]
 '[mattsum.boot-react-native    :as     rn]
 )

(deftask build
  [p platform PLATFORM kw "The platform to target (ios or android)"]
  []
  (assert (or (nil? platform) (#{:ios :android} platform)))
  (comp
   (if (= :ios platform) (rn/run-in-simulator) identity)
   (reload :on-jsload 'mattsum.simple-example.core/on-js-reload
           :port 8079
           :ws-host "localhost"
           )
   (rn/before-cljsbuild)

   (cljs-repl :ws-host "localhost"
              :port 9001
              :ip "0.0.0.0")

   (speak)
   (cljs :main "mattsum.simple-example.core"
         :ids #{"main"})
   (rn/after-cljsbuild :server-url "localhost:8081")
   (if (= :ios platform) (rn/print-ios-log) identity)
   (if (= :android platform) (rn/print-android-log) identity)
   (target :dir ["app/build"])))

(deftask dev
  "Build app and watch for changes"
  [p platform PLATFORM kw "The platform to target (ios or android)"]
  []
  (comp (watch)
        (if platform (build :platform platform) (build))))

(deftask packager
  []
  (watch)
  (rn/start-rn-packager))

(defn ^:private file-by-path [path fileset]
  (b/tmp-file (get (:tree fileset) path)))

(deftask dist
  "Build a distributable bundle of the app"
  []
  (comp
   (cljs :ids #{"dist"})
   (rn/bundle :files {"dist.js" "main.jsbundle"})
   (target :dir ["app/dist"])))
