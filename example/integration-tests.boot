#!/usr/bin/env boot

(set-env! :dependencies '[
                          [org.clojure/clojure            "1.7.0"]
                          [debug-repl "0.3.2"]
                          [org.clojure/tools.nrepl "0.2.11"]
                          [camel-snake-kebab "0.3.2"]
                          [io.appium/java-client "3.2.0"]])


(load-file "app_testing/appium.clj")
(load-file "app_testing/core.clj")
(require '[clojure.test :refer [deftest is run-all-tests testing] :as test]
         '[clojure.reflect :as r]
         '[clojure.string :as str]
         '[clojure.tools.nrepl :as repl]
         '[app-testing.core :refer [testing-on-device run-in-app-repl
                                    wait-for-text] :as app])
(use 'alex-and-georges.debug-repl)
(use '[clojure.pprint :only [pprint]])

(deftest repl-commands
  (testing-on-device
   "App should respond to repl commands"
   (run-in-app-repl
    (require '[mattsum.simple-example.core :as core :refer [view text]]
             '[reagent.core :as reag])
    (reag/render [core/view [core/text "CHANGED BY REPL"]] 1))
   (wait-for-text "CHANGED BY REPL"))
  (testing-on-device
   "Println should work"
   (run-in-app-repl
    (println "THIS SHOULD NOT THROW AN EXCEPTION"))))

(deftest reloading
  (testing-on-device
   "App should reload if source files change"
   (let [source-path "src/mattsum/simple_example/core.cljs"
         content (slurp source-path)]
     (try
       (spit source-path (str/replace content "CHANGE THIS: HELLO WORLD" "CHANGE THIS: GOODBYE WORLD"))
       (wait-for-text "GOODBYE WORLD")
       (spit source-path (str/replace content "CHANGE THIS: GOODBYE WORLD" "CHANGE THIS: HELLO WORLD"))
       (wait-for-text "HELLO WORLD")
       (finally
         (spit source-path content))))))

(defn -main [& args]
  "Simple integration test for reloading. Requires boot and packager to have started up before running. Also requires appium server to be running (npm install -g appium && appium).
Also ensure that Android device is connected via adb and that SimpleExampleApp has been installed (gradle installDebug)."
  (test/run-tests)

  )
