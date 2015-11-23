(ns mattsum.boot-rn
  {:boot/export-tasks true}
  (:require [boot.core :as c :refer [deftask with-pre-wrap]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.pprint :refer [pprint]]
            [boot.file :as fl]
            [boot.util :as util]
            [clojure.java.io :as io]
            [me.raynes.conch.low-level :as conch]
            [me.raynes.conch :refer [with-programs]]))
;;(use 'alex-and-georges.debug-repl)


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

(defn find-file [fileset path]
  (some->> path
           (c/tmp-get fileset)
           (c/tmp-file)))

(defn add-provides-module-metadata
  "Adds react's @providesModule metadata to javascript file"
  [source-file target-file module-name]
  (let [content (slurp source-file)
        new-content (str content "\n/* \n * @providesModule " (.replace module-name ".js" "") "\n */\n")]
    (spit target-file new-content)))

(defn new-hard-link [src target]
  (when (fl/exists? target)
    (fl/delete-file target))
  (fl/hard-link src target))

(defn setup-links-for-dependency-map
  [files-to-process]
  (doseq [{:keys [target-file
                  source-file
                  map-file
                  cljs-file
                  target-map
                  target-cljs
                  ns]} files-to-process])
      (when source-file
        (io/make-parents target-file)
        (add-provides-module-metadata source-file target-file ns)
        (when (and (fl/exists? map-file)
                   (fl/exists? cljs-file))
          (new-hard-link map-file target-map)
          (new-hard-link cljs-file target-cljs))))))

(defn- get-dependency-mappings-from-file
  [dep-file]
  (->> dep-file
       (str src-dir)
       (find-file fileset)
       (get-deps)))

(defn- get-all-dependency-mappings
  [deps-files]
  (flatten (map get-dependency-mappings-from-file deps-files)))

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

(defn- get-files-to-process
  [deps-files fileset tmp-dir src-dir]
  (-> deps-files
      get-all-dependency-mappings
      (get-dependency-files-from-mappings fileset tmp-dir src-dir)))

(deftask link-goog-deps
  "Parses Google Closure deps files, and creates a link in the output node_modules directory to each file.

   This makes it possible for the React Native packager to pick up the dependencies when building the JavaScript Bundle allowing us to develop with :optimizations :none"
  [d deps-files DEPS #{str}  "A list of relative paths to deps files to parse"
   o cljs-dir OUT str  "The cljs :output-dir"]
  (let [previous-files (atom nil)
        output-dir (c/tmp-dir!) ; Create the output dir in outer context allows us to cache the compilation, which means we don't have to re-parse each file
        ]
    (with-pre-wrap fileset
      (let [get-hash-diff #(c/fileset-diff @previous-files % :hash)

            new-files     (->> fileset
                               get-hash-diff)
            deps-files    (or deps-files ["cljs_deps.js" "goog/deps.js"])
            src-dir       (str (or cljs-dir "main.out") "/")]
        (reset! previous-files fileset)

        (util/info "Compiling {cljs-deps}... %d changed files\n" (count new-files) )
        (let [files-to-process (get-files-to-process deps-files fileset output-dir src-dir)]
          (setup-links-for-dependency-map files-to-process))

        (-> fileset
            (c/add-resource output-dir)
            c/commit!)))))



(deftask replace-main
  "Replaces the main.js with a file that can be read by React Native's packager"
  [o output-dir OUT str  "The cljs :output-dir"]
  (let []
    (with-pre-wrap fileset
      (let [out-dir (str (or output-dir "main.out") "/")
            tmp (c/tmp-dir!)
            main-file (->> "main.js"
                           (find-file fileset))
            boot-main (->> main-file
                           slurp
                           (re-find #"(boot.cljs.\w+)\""))
            boot-main (get boot-main 1)
            out-file (io/file tmp "main.js")
            new-script (str "
var CLOSURE_UNCOMPILED_DEFINES = null;
require('./" out-dir "goog/base.js');
require('" boot-main "');
")]
        (spit out-file new-script)
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(defn read-resource
  [src]
  (->> src io/resource slurp))

(deftask append-to-goog
  "Appends some javascript code to goog/base.js in order for React Native to work with Google Closure files"
  [o output-dir OUT str  "The cljs :output-dir"]
  (let [tmp (c/tmp-dir!)]
    (with-pre-wrap fileset
      (let [out-dir (str (or output-dir "main.out") "/")
            base-file (->> "goog/base.js"
                           (str out-dir)
                           (find-file fileset))
            base-content (->> base-file
                              slurp)
            out-file (io/file (str tmp "/" out-dir "/goog") "base.js")
            new-script (->> "mattsum/boot_rn/js/goog_base.js" io/resource slurp)
            out-content (str base-content "\n" new-script)]
        (doto out-file
          io/make-parents
          (spit out-content))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(deftask react-native-devenv []
  (comp (link-goog-deps)
     (replace-main)
     (append-to-goog)))

;from boot-restart
(defn shell [command]
  (let [args (remove nil? (clojure.string/split command #" "))]
    (assert (every? string? args))
    (let [process (apply conch/proc args)]
      (future (conch/stream-to-out process :out))
      (future (conch/stream-to process :err (System/err)))
      process)))

(defn kill [process]
  (when-not (nil? process)
    (conch/destroy process)))

(defn exit-code [process]
  (future (conch/exit-code process)))

(deftask start-rn-packager
  "Starts the React Native packager. Includes a custom transformer that skips transformation for ClojureScript generated files."
  [a app-dir OUT str  "The (relative) path to the React Native application"]
  (let [app-dir (or app-dir "app")
        command (str app-dir "/node_modules/react-native/packager/packager.sh --transformer app/cljs-rn-transformer.js")
        process (atom nil)]
    (c/with-pre-wrap fileset
      (let [start-process #(reset! process (shell command))]
        (when (nil? @process)
          (start-process))
        (let [exit (exit-code @process)]
          (when (realized? exit) ;;restart server if necessary
            (if (= 0 @exit)
              (util/warn "Process exited normally, restarting.")
              (util/fail "Process crashed, restarting."))
            (start-process))))
      fileset))) 
