(ns mattsum.impl.goog-deps
  (:require [boot
             [file :as fl]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [mattsum.impl.boot-helpers :refer [find-file]]))

(defn split-line [line]
  (when-let [res (re-find #"[\"|'](.*?)[\"|'],\s(\[\'.*?\'\])" line)]
    [(res 1) (res 2)]))

(defn parse-deps [[path namespaces]]
  (when path
    (for [ns (s/split namespaces #",")]
      (let [clean-ns (s/replace ns #"[\[|\]|\']" "")
            path     (if (.startsWith path "../")
                       (.replace path "../" "")
                       (str "goog/" path))]
        {:path path
         :ns (s/trim (str clean-ns ".js"))}))))

(defn get-deps [deps-file]
  (let [contents (slurp deps-file)
        lines    (s/split contents #"\n")
        results  (filter identity (map split-line lines))
        commands (map parse-deps results)]
    (flatten commands)))

(defn add-provides-module-metadata
  "Adds react's @providesModule metadata to javascript file"
  [source-file target-file module-name]
  (let [content (slurp source-file)
        new-content (str content "\n/* \n * @providesModule " (.replace module-name ".js" "") "\n */\n")]
    (spit target-file new-content)))

(defn update-sourcemap-url
  [source-file target-file module-name]
  (let [content (slurp source-file)
        new-content (s/replace content #"sourceMappingURL=(.*)" (str "sourceMappingURL=" (str module-name ".map")))]
    (spit target-file new-content)))

(defn new-hard-link [src target]
  (when (fl/exists? target)
    (fl/delete-file target))
  (fl/hard-link src target))

(defn- get-all-dependency-mappings
  [deps-files src-dir fileset]
  (flatten (map #(->> %1
                      (str src-dir)
                      (find-file fileset)
                      (get-deps)) deps-files)))

(defn- get-dependency-files-from-mappings
  "Responsible for converting a list of dependency maps read from cljs_deps.js or deps.js into a data structure
   that can be used the set up files in directory structure that React Native can use"
  [dependency-maps fileset tmp-dir src-dir]
  (map (fn [{:keys [ns path]}]
         {:target-file (io/file (str tmp-dir "/node_modules") ns)
          :source-file (some->> (str src-dir path)
                                (find-file fileset))
          :map-file    (some->> (str src-dir path ".map")
                                (find-file fileset))
          :cljs-file   (some->> (.replace (str src-dir path) ".js" ".cljs")
                                (find-file fileset))
          :target-map (io/file (str tmp-dir "/node_modules") (str ns ".map"))
          :target-cljs (io/file (str tmp-dir "/node_modules") (.replace ns ".js" ".cljs"))
          :ns ns
          })
       dependency-maps))

(defn get-files-to-process
  [deps-files fileset tmp-dir src-dir]
  (-> deps-files
      (get-all-dependency-mappings src-dir fileset)
      (get-dependency-files-from-mappings fileset tmp-dir src-dir)))

(defn setup-links-for-dependency-map
  [files-to-process]
  (doseq [{:keys [target-file
                  source-file
                  map-file
                  cljs-file
                  target-map
                  target-cljs
                  ns]} files-to-process]
    (when source-file
      (io/make-parents target-file)
      (add-provides-module-metadata source-file target-file ns)
      (update-sourcemap-url target-file target-file ns)
      (when (and (fl/exists? map-file)
                 (fl/exists? cljs-file))
        (new-hard-link map-file target-map)
        (new-hard-link cljs-file target-cljs)))))

