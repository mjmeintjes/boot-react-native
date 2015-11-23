(ns mattsum.simple-log-server
  (:require [clojure.pprint :refer [pprint]]
            [cemerick.url :refer [url-decode]]))

(defn log [request]
  (let [content (->> request
                     :body
                     slurp)]
    (println (str (url-decode (:uri request)) " - " content))
  {:status 200
   :headers {"Access-Control-Allow-Origin" "*"}}))
