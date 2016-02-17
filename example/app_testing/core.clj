(ns app-testing.core
  (:import
   [io.appium.java_client android.AndroidDriver
                           AppiumDriver
                           MobileElement
                           ]
   java.net.URL
   io.appium.java_client.remote.MobileCapabilityType
   org.openqa.selenium.remote.DesiredCapabilities)
  (:require  [app-testing.appium :as app]
             [clojure.tools.nrepl :as repl]
             [clojure.test :as t]
             [clojure.pprint :refer [pprint]]))

(defn get-driver
  []
  (let [cap (DesiredCapabilities.)]
    (doto cap
      (.setCapability MobileCapabilityType/PLATFORM_VERSION "4.4.4")
      (.setCapability MobileCapabilityType/DEVICE_NAME "Android Genymotion") ;ignored but necessary
      (.setCapability "appPackage" "com.simpleexampleapp")
      (.setCapability MobileCapabilityType/APP_ACTIVITY ".MainActivity")
      (.setCapability "noReset" false)
      (.setCapability "newCommandTimeout" 6000))

    (let [driver  (AndroidDriver. (URL. "http://127.0.0.1:4723/wd/hub") cap)]
      (.addShutdownHook (Runtime/getRuntime) (Thread. (fn[] (when driver
                                                             #_(println "Closing AndroidDriver")
                                                             (.quit driver)))))
      driver)))

(defn wait-for
  ([pred msg] (wait-for pred msg 15))
  ([pred msg timeout-seconds]
   (let [wait-time 2000
         timeout (* timeout-seconds 1000)]
     (loop [loops 0]
       (when (> (* loops wait-time) timeout)
         (throw (ex-info (str "Timeout while waiting - " msg) {})))
       (let [res (pred)]
         #_(println "Wait for returned " res)
         (when (not res)
           (Thread/sleep wait-time)
           (recur (inc loops))))))))

(defn by-text
  [text]
  (str "//*[contains(@text, \"" text "\")]"))

(defn find-elements-by-text
  [text]
  (app/run find-elements-by-xPath (by-text text)))

(defn wait-for-equal
  [eq-test result-fn msg timeout]
  (wait-for (fn[]
              (let [res (result-fn)]
                (if (= res eq-test)
                  res
                  (do
                    #_(println "Retrying - " res " not equal to " eq-test)))))
            msg
            timeout))

(defn wait-for-text
  [text]
  (wait-for #(first (find-elements-by-text text)) text))

(defn reload-js []
  (app/run press-key-code 82 (Integer. 1))
  (wait-for-text "Reload JS")
  (.click (first (app/run find-elements-by-xPath "//*[@text=\"Reload JS\"]"))))

(defn response-values-or-errors
  "Given a seq of responses (as from response-seq or returned from any function returned
   by client or client-session), returns a seq of values read from :value slots found
   therein, or errors if exception was found."
  [responses]
  #_(println "Converting responses " responses " to values")
  (let [res (->> responses
                 (map #(try
                         (repl/read-response-value %)
                         (catch RuntimeException e
                           %)))
                 repl/combine-responses
                 )]
    (when-let [ex (:ex res)]
      (throw (ex-info (:err res) res)))
    (:value res)
    ))

(defn eval-repl-command
  [sess command]
  #_(println "Evaluating " command " in session")
  (-> (repl/message sess {:op :eval :code command})
      response-values-or-errors))

(def cljs-session (atom nil))

(defn wait-for-cljs-startup
  [sess]
  (wait-for-equal
   ["Success"]
   #(eval-repl-command sess "(clj->js \"Success\")")
   "Cljs Repl Startup"
   60))

(defn start-cljs-repl
  ([] (start-cljs-repl nil))
  ([reload]
   (when (= nil @cljs-session)
     #_(println "Starting CLJS session")
     (let [conn (repl/connect :port (Integer. (slurp ".nrepl-port")))
           sess (-> (repl/client conn 1000)
                    (repl/client-session))]
       (.addShutdownHook (Runtime/getRuntime) (Thread. (fn[] (when conn (.close conn)))))
       (when reload
         (reload-js))
       (repl/message sess {:op :eval :code "(start-repl)"})
       (try
         (wait-for-cljs-startup sess)
         (catch Exception e
           (if (not reload)
             (start-cljs-repl true)
             (throw e))))
       (reset! cljs-session sess)))
   @cljs-session))

(defmacro run-in-app-repl
  [& commands]
  (let [to-run (map (fn[command]
                      `(let [sess# (start-cljs-repl)]
                         #_(println "Running " (repl/code ~command))
                         (eval-repl-command sess# (repl/code ~command))))
                    commands)]
    `(do ~@to-run)))

(defmacro testing-on-device
  [msg & test]
  `(test/testing ~msg
     (let [driver# (get-driver)]
       (try
         (app/run-on-device false driver#
                            (fn[]
                              (reload-js)
                              (wait-for-text "HELLO WORLD")
                              (test/is (= true
                                          (do
                                            ~@test
                                            true))
                                       "")))
         (finally
           (.quit driver#))))))

(comment
  (pprint (macroexpand-1 '(testing-on-device "test" (println test1))))
  (pprint (macroexpand-1 '(run-in-app-repl
                           (println "test")
                           (println "test2")))))
