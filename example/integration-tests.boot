#!/usr/bin/env boot

(set-env! :dependencies '[
                          [debug-repl "0.3.2"]
                          [camel-snake-kebab "0.3.2"]
                          [io.appium/java-client "3.2.0"]])
(import
 '(io.appium.java_client android.AndroidDriver
                         AppiumDriver
                         MobileElement
                         )
 java.net.URL
 io.appium.java_client.remote.MobileCapabilityType
 org.openqa.selenium.remote.DesiredCapabilities)

(load-file "testing/utils.clj")
(load-file "testing/appium.clj")
(require '[clojure.reflect :as r]
         '[appium-clj.appium :as app]
         '[clojure.string :as str])
(use 'alex-and-georges.debug-repl)
(use '[clojure.pprint :only [pprint]])

(defn get-driver
  []
  (let [cap (DesiredCapabilities.)]
    (doto cap
      (.setCapability MobileCapabilityType/PLATFORM_VERSION "4.4.4")
      (.setCapability MobileCapabilityType/DEVICE_NAME "Android Genymotion") ;ignored but necessary
      ;(.setCapability MobileCapabilityType/APP "./app/android/app/build/outputs/apk/app-debug.apk")
      (.setCapability "appPackage" "com.simpleexampleapp")
      (.setCapability MobileCapabilityType/APP_ACTIVITY ".MainActivity")
    (.setCapability "noReset" true)
    (.setCapability "newCommandTimeout" 600))

    (let [driver  (AndroidDriver. (URL. "http://127.0.0.1:4723/wd/hub") cap)]
      (.addShutdownHook (Runtime/getRuntime) (Thread. (fn[] (when driver
                                                             (println "Closing AndroidDriver")
                                                             (.quit driver)))))
      driver)))

(defn wait-for
  [pred msg]
  (loop [loops 0]
    (when (> loops 10)
      (throw (ex-info (str "Timeout while waiting - " msg) {})))
    (when (not (pred))
      (Thread/sleep 2000)
      (recur (inc loops)))))

(defn by-text
  [text]
  (str "//*[contains(@text, \"" text "\")]"))

(defn find-elements-by-text
  [text]
  (app/run find-elements-by-xPath (by-text text)))
 
(defn wait-for-text
  [text]
  (wait-for #(first (find-elements-by-text text)) text))

(defn reload-js []
  (app/run press-key-code 82 (Integer. 1))
  (.click (first (app/run find-elements-by-xPath "//*[@text=\"Reload JS\"]"))))

(defn -main [& args]
  "Simple integration test for reloading. Requires boot and packager to have started up before running. Also requires appium server to be running (npm install -g appium && appium).
Also ensure that Android device is connected via adb and that SimpleExampleApp has been installed (gradle installDebug)."

  (let [driver (get-driver)
        source-path "src/mattsum/simple_example/core.cljs"
        content (slurp source-path)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn[] (spit source-path content))))

    (app/run-on-device
     false driver
     (fn []
       (reload-js)

       (wait-for-text "HELLO WORLD")
       (spit source-path (str/replace content "CHANGE THIS: HELLO WORLD" "CHANGE THIS: GOODBYE WORLD"))
       (wait-for-text "GOODBYE WORLD")
       (spit source-path (str/replace content "CHANGE THIS: GOODBYE WORLD" "CHANGE THIS: HELLO WORLD"))
       (wait-for-text "HELLO WORLD")
       (println "")
       (println "")
       (println "")
       (println "----------------------------------------------------")
       (println "INTEGRATION TEST RESULTS")
       (println "========================")
       (println "Hot reloading works")
       (println "----------------------------------------------------")
       (println "")
       (println "")
       (println "")

       ))
    ))
