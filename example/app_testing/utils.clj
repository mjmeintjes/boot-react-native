(ns ^{:doc "iOS Utility functions
            Reference: https://github.com/kapilreddy/calabash-clj"}
    app-testing.util
  (:require [clojure.java.shell :as shell]))

(defn run-sh
  "Passes the given command to shell/sh to exexute in sub-process."
  [& commands]
  (let [op (apply shell/sh (clojure.string/split (first commands) #"\s"))]
    (if (empty? (rest commands))
      op
      (recur (rest commands)))))


(defn run-with-dir
  "Passes the given command to shell/sh to exexute command
with given directory."
  [dir & commands]
  (binding [shell/*sh-dir* dir]
    (apply run-sh commands)))


(defn some-truthy
  "Check to see if the input map contains at least one of the keys ks
  The value against the key should be non-nil."
  [m & ks]
  (some #(not= (m %) nil) ks))
