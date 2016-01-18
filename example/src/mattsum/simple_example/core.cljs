(ns mattsum.simple-example.core
  (:require [reagent.core :as reag :refer [atom]]
            [cljs.test :as test]))

#_(enable-console-print!)


(set! js/React (js/require "react-native/Libraries/react-native/react-native.js"))

(def view (reag/adapt-react-class (.-View js/React)))
(def text (reag/adapt-react-class (.-Text js/React)))

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

(defn main
  []
  (js/console.log "MAIN")
  (mount-root))

(test/deftest testingt
  (test/is (= 1 2) "ERROR"))

(defn on-js-reload
  []
  (enable-console-print!)
  (test/run-tests)
  (js/console.log "JS RELOADING")
  (mount-root))
