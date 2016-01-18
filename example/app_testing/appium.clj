(ns app-testing.appium
  (:require [clojure.java.io :as io]
            [app-testing.utils :refer [ppxml]]
            [camel-snake-kebab.core :as cm])
  (:import io.appium.java_client.AppiumDriver
           [org.openqa.selenium WebElement By]))


(def ^:dynamic *appium-driver* nil)


(defn reset-app
  "Reset the currently running app for this session."
  []
  (. ^AppiumDriver *appium-driver* resetApp))


(defn run-on-device
  "Executes test-fn with appium-driver."
  [reset? appium-driver test-fn]
  (binding [*appium-driver* appium-driver]
    (when reset?
      (reset-app))
    (test-fn)))

(defmacro run
  [command & args]
  (let [command-name (cm/->camelCase (str command))]
    `(do (println)
         (println)
         (println "Running " ~command-name)
         (println "DEBUG - current app state:"
                  (ppxml (. ^AppiumDriver *appium-driver* getPageSource)))
         (. ^AppiumDriver *appium-driver* ~(symbol command-name) ~@args))))
