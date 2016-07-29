(ns mattsum.simple-example.core
  (:require [reagent.core :as r]
            [cljs.test :as test]))

(enable-console-print!)
(println "evaluating mattsum.simple-example.core...")

;; we need set! for advanced compilation

(set! js/React (js/require "react-native/Libraries/react-native/react-native.js"))
(defonce react (js/require "react-native/Libraries/react-native/react-native.js"))

(def view (r/adapt-react-class (.-View react)))
(def text (r/adapt-react-class (.-Text react)))
(def touchable-highlight (r/adapt-react-class (.-TouchableHighlight js/React)))

(defonce !state (r/atom {:count 0}))
(println "after defonce:" (pr-str @!state))


(defn root-view
  []
  (println "root-view render:" (pr-str @!state))
  [view {:style {:margin-top 22, :margin-left 8}}
   [text "asdf"]
   [touchable-highlight {:on-press (fn []
                                     (println "before swap. state:" (pr-str @!state))
                                     (swap! !state update :count inc)
                                     (println "after swap. state:" (pr-str @!state)))
                         :underlay-color "#00ff00"}
    [text (str "Count: " (:count @!state) ", click to increase")]]])


(defn ^:export main
  []
  (js/console.log "MAIN")
  (enable-console-print!)
  (.registerComponent (.-AppRegistry react)
                      "SimpleExampleApp"
                      #(r/reactify-component #'root-view)))

(defn on-js-reload
  []
  (println "on-js-reload. state:" (prn @!state))
  ;; Force re-render
  ;;
  ;; In React native, there are no DOM nodes. Instead, mounted
  ;; components are identified by numbers. The first root components
  ;; is assigned the number 1.

  (r/render #'root-view 1))
