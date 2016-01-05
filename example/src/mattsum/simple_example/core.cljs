(ns mattsum.simple-example.core
  (:require [reagent.core :as reag :refer [atom]]))

(enable-console-print!)

(set! js/React (js/require "react-native/Libraries/react-native/react-native.js"))

(def view (reag/adapt-react-class (.-View js/React)))
(def text (reag/adapt-react-class (.-Text js/React)))

(defn root-view
  []
  [view
   [text "CHANGE THIS: HELLO WORLD"]])

(defn mount-root []
  (reag/render [root-view] 1))

(defn main
  []
  (js/console.log "MAIN")
  (mount-root))


(defn on-js-reload
  []
  (js/console.log "JS RELOADING")
  (mount-root))
