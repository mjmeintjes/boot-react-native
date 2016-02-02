(ns mattsum.simple-example.core
  (:require [reagent.core :as reag :refer [atom]]
            [cljs.test :as test]))

#_(enable-console-print!)

;; we need set! for advanced compilation

(set! js/React (js/require "react-native/Libraries/react-native/react-native.js"))
(defonce react (js/require "react-native/Libraries/react-native/react-native.js"))

(def view (reag/adapt-react-class (.-View react)))
(def text (reag/adapt-react-class (.-Text react)))

(defn testf
  []
  (println "TESTING")
  "RETURN TESTING")

(defn root-view
  []
  [view
   [text {:style {:margin-top 22, :margin-left 8}} "CHANGE THIS: HELLO WORLD"]])

(defn mount-root []
  (reag/render [root-view] 1))

(defn ^:export main
  []
  (js/console.log "MAIN")
  (.registerComponent (.-AppRegistry react)
                      "SimpleExampleApp"
                      #(reag/reactify-component root-view)))

(test/deftest testingt
  (test/is (= 1 2) "ERROR"))

(defn on-js-reload
  []
  (enable-console-print!)
  (test/run-tests)
  (js/console.log "JS RELOADING")
  (mount-root))
