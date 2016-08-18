(set-env!
 :source-paths   #{"src" "react-support"}
 :resource-paths   #{"resources"}
 :exclusions ['cljsjs/react]
 :dependencies '[
                 [boot-react-native/boot-react-native      "0.3-rc2" :scope "test"]
                 [adzerk/boot-cljs               "1.7.228-1"       :scope  "test"]
                 [adzerk/boot-cljs-repl          "0.3.3"           :scope  "test"]
                 [adzerk/boot-reload             "0.4.12"          :scope  "test"]
                 [com.cemerick/piggieback        "0.2.1"           :scope  "test"]
                 [weasel                         "0.7.0"           :scope  "test"]
                 [org.clojure/tools.nrepl        "0.2.12"          :scope  "test"]
                 [org.clojure/clojure            "1.8.0"]
                 [org.clojure/clojurescript      "1.8.51"]
                 [reagent                        "0.6.0-rc"]
                 ;; [react-native-externs "0.0.1-SNAPSHOT"]
                 ]
 )

(require
 '[adzerk.boot-cljs             :refer  [cljs]]
 '[adzerk.boot-cljs-repl        :refer  [cljs-repl  start-repl]]
 '[adzerk.boot-reload           :refer  [reload]]
 '[boot.core                    :as     b]
 '[boot.util                    :as     u]
 '[clojure.string               :as     s]
 '[mattsum.boot-react-native    :as     rn :refer [patch-rn]]
 )

(task-options! patch-rn {:app-dir "app"})

(deftask build
  []
  (comp
   (reload :on-jsload 'mattsum.simple-example.core/on-js-reload
           :port 8079
           :ws-host "localhost")
   (rn/before-cljsbuild)
   (cljs-repl :ws-host "localhost"
              :port 9001
              :ip "0.0.0.0")
   (speak)
   (cljs :ids #{"main"})
   (rn/after-cljsbuild :server-url "localhost:8081")
   (target :dir ["app/build"])))

(deftask dev
  "Build app and watch for changes"
  []
  (comp (patch-rn)
        (watch)
        (build)))

(deftask dist
  "Build a distributable bundle of the app"
  []
  (comp
   (patch-rn)
   (cljs :ids #{"dist"})
   (rn/bundle :files {"dist.js" "main.jsbundle"})
   (target :dir ["app/dist"])))
