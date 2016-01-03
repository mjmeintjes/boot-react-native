(ns mattsum.simple-example.core)

(enable-console-print!)

;; Change the messages and watch the new messages being printed to RN console log on save

(defn main
  []
  (js/console.log "HELLO WORLD 3"))


(defn on-js-reload
  []
  (js/console.log "JS RELOADING 1")
  (main))
