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
         '[appium-clj.appium :as app])
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

    (AndroidDriver. (URL. "http://127.0.0.1:4723/wd/hub") cap)))

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

(defn -main [& args]
  #_(pprint (macroexpand-1 '(app/run press-key-code 82)))
  (let [driver (get-driver)]
    (try
      (app/run-on-device
       false driver
       (fn []
         (wait-for-text "Welcome to React")
         (debug-repl)
         ;; (app/run press-key-code 82 (Integer. 1))
         ;; (.click (first (app/run find-elements-by-xPath "//*[@text=\"Reload JS\"]")))
         ;; (.click (first (app/run find-elements-by-xPath "//*[contains(@text, \"Debug server\")]")))
         ;;(.sendKeys (first (app/run find-elements-by-xPath "//*[contains(@class, \"android.widget.EditText\")]")) (into-array ["matt-dev:8081"]))
         ;;(println (app/run find-elements-by-xPath "//*[@text=\"OK\"]"))
         ;;(.click (first (app/run find-elements-by-xPath "//*[@text=\"OK\"]")))
         ;(app/run press-key-code 4 nil)

         ))
      (finally
        (.quit driver)))))
