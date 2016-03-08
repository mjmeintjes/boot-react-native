(ns mattsum.impl.boot-helpers
  (:require [boot.core :as c]
            [boot.util :as util]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.conch.low-level :as conch]))

(defn find-file [fileset path]
  (some->> path
           (c/tmp-get fileset)
           (c/tmp-file)))

(defn read-resource-
  [src]
  (println "Reading resource - " src)
  (->> src io/resource slurp))

;; For some reason Clojure/boot doesn't like us reading from the jar file too often,
;; so lets just do it once per resource path.
(def read-resource (memoize read-resource-))

(defn make-absolute
  "Makes a relative path absolute using the provided tmp dir"
  [tmp rel-path]
  (str tmp "/" rel-path))

(defn not-nil
  [msg obj]
  (when (nil? obj)
    (throw (ex-info msg {})))
  obj)

(defn apply-replacements
  "Applies a list of [match-string new-string] replacements on content"
  [content replacements]
  (reduce #(apply str/replace %1 %2) content replacements))

(defn modify-file
  "Modifies a file, specified by relative path, and returns
  the new fileset"
  ([fileset path modify-fn] (modify-file fileset path modify-fn {}))
  ([fileset path modify-fn replacements]
   (println "Modifying " path " using " modify-fn)
   (let [tmp (c/tmp-dir!)
         out-file (make-absolute tmp path)]
     (let [base-file (->> path
                          (find-file fileset)
                          (not-nil (str "Could not modify file - not found in fileset - " path)))
           base-content (->> base-file
                             slurp)
           out-content (some-> base-content
                               modify-fn
                               (apply-replacements replacements) )]

       (doto out-file
         io/make-parents
         (spit out-content))
       (-> fileset
           (c/add-resource tmp)
           )))))

(defn append-to-file
  [fileset path content replacements]
  (modify-file fileset path #(str % "\n" content) replacements))

(defn prepend-to-file
  [fileset path content replacements]
  (modify-file fileset path #(str content "\n" %) replacements))

(defn add-resource-to-file
  "Reads a resource file stored in the jar, and appends it to file in 'path'"
  ([fileset path resource-path replacements modify-fn]
   (let [new-script (read-resource resource-path)]
     (modify-fn fileset path new-script replacements)
     )))

(defn output-file-path
  [output-dir output-file-path]
  (let [out-dir (str (or output-dir "main.out") "/")
        out-file (str out-dir output-file-path)]
        out-file))


(defn write-resource-to-path
  "Copies the provided file in jar resources to the provided output path."
  [resource-path output-path]
  (let [tmp (c/tmp-dir!)
        out-file (io/file tmp output-path)
        resource-content (read-resource resource-path)]
    (println "Writing " resource-path " to " out-file)
    (doto out-file
      io/make-parents
      (spit resource-content))
    tmp))

(defn write-resource-to-fileset
  [fileset resource-path output-path]
  (->> (write-resource-to-path resource-path output-path)
       (c/add-resource fileset)))

(defn write-cljs!
  "Writes clj template (using boot.from.backtick/template) to a file and returns the
  path to the containing directory (for inclusion in fileset)"
  [out-relative-path cljs-template]
  (let [out-dir (c/tmp-dir!)
        out-file (io/file out-dir out-relative-path)]
    (io/make-parents out-file)
    (util/info "Writing cljs-template to %s...\n" (.getName out-file))
    (->> cljs-template
         (map pr-str)
         (interpose "\n")
         (apply str)
         (spit out-file))
    out-dir))

(defn write-cljs-to-fileset!
  [fileset out-relative-path cljs-template]
  (->> (write-cljs! out-relative-path cljs-template)
       (c/add-source fileset)))

(defn add-cljs-require!
  "Adds a ns :require to the provided edn file"
  [ns edn-in-file out-file]
  (let [spec (-> edn-in-file slurp read-string)]
    (when (not= :nodejs (-> spec :compiler-options :target))
      (util/info "Adding :require %s to %s...\n" ns (.getName edn-in-file))
      (io/make-parents out-file)
      (-> spec
          (update-in [:require] conj ns)
          pr-str
          ((partial spit out-file))))))

(defn get-cljs-edn-files [fileset ids]
  "Returns a list of edn files found in fileset.
   Either returns all of them, or just the ones specified by ids"
  (let [relevant  (map #(str % ".cljs.edn") ids)
        f         (if ids
                    #(c/by-path relevant %)
                    #(c/by-ext [".cljs.edn"] %))]
    (-> fileset c/input-files f)))

(defn add-cljs-require-to-edn-files
  [fileset ids ns]
  (let [tmp (c/tmp-dir!)]
    (println "Found edn files - " (get-cljs-edn-files fileset ids))
    (doseq [edn-file (get-cljs-edn-files fileset ids)]
      (let [path (c/tmp-path edn-file)
            edn-file (c/tmp-file edn-file)
            out-file (io/file tmp path)]
        (add-cljs-require! ns edn-file out-file)))
    (-> fileset
        (c/add-resource tmp))))

(defn add-cljs-template-to-fileset
  "Adds and requires a cljs template to the fileset"
  [fileset ids ns template]
  (let [path (-> (str ns)
                 (str/replace #"\." "/")
                 (str/replace #"-" "_"))]
    (-> fileset
        (write-cljs-to-fileset! (str path ".cljs") template)
        (add-cljs-require-to-edn-files ids ns))))

(defn shell [command]
  (let [args (remove nil? (clojure.string/split command #" "))]
    (assert (every? string? args))
    (let [process (apply conch/proc args)]
      (future (conch/stream-to-out process :out))
      (future (conch/stream-to process :err (System/err)))
      process)))

(defn sh*
  [& args]
  (let [args (remove nil? args)]
    (assert (every? string? args))
    (let [opts (into [:redirect-err true] (when util/*sh-dir* [:dir util/*sh-dir*]))
          proc (apply conch/proc (concat args opts))]
      (future (conch/stream-to-out proc :out))
      (future (conch/stream-to proc :err (System/err)))
      proc)))


(defn kill [process]
  (when-not (nil? process)
    (conch/destroy process)))

(defn exit-code [process]
  (future (conch/exit-code process)))

(defn newest-log []
  (some->> (str (System/getProperty "user.home") "/Library/Logs/CoreSimulator")
           clojure.java.io/file
           file-seq
           (filter #(->> % .getName (= "system.log")))
           (sort-by #(.lastModified %) #(compare %2 %1))
           first
           .getPath))

(defn file-by-path [path fileset]
  (c/tmp-file (get (:tree fileset) path)))

(defn copy-file [source-path dest-path]
  (clojure.java.io/copy (clojure.java.io/file source-path) (clojure.java.io/file dest-path)))

(defn bundle* [app-dir in outf outd]
  (let [app-dir (or app-dir "app")
        tempfname (str "nested/temp/" (java.util.UUID/randomUUID) ".js")
        temppath (str app-dir "/" tempfname)
        tempdirf (->> temppath clojure.java.io/as-file .getParentFile)
        cli (-> (str app-dir "/node_modules/react-native/local-cli/cli.js")
                java.io.File.
                .getAbsolutePath)
        dir (-> in .getAbsoluteFile .getParent)
        fname (-> in .getAbsolutePath)]
    (util/info "Bundling %s...\n" fname)
    ;; create nested temp directory
    ;; we need this in order to keep the react packager happy
    (util/info "Creating temp dir: %s\n" (.getAbsolutePath tempdirf))
    (.mkdirs tempdirf)
    (copy-file fname temppath)
    (binding [util/*sh-dir* app-dir]
      (try
        (util/dosh "node" cli
                   "bundle" "--platform" "ios"
                   "--dev" "true"
                   "--entry-file" tempfname
                   "--bundle-output" (.getAbsolutePath outf)
                   "--assets-dest" (.getAbsolutePath outd))
        (finally
          (util/dosh "rm" "-f" tempfname))))))

(defn ->grep [only]
  (assert (string? only))
  (assert (and (not (.contains only "'")) (not (.contains only "\\")))
          "Search term must not contain special chars")
  ["bash" "-c" (format  "\"$@\" | grep --line-buffered -F '%s'" only) "ignore-first-arg"])

(defn tail-cmd [fname only]
  (let [prefix (if only (->grep only) [])]
    (concat prefix ["tail" "-0f" fname])))

(defn tail-fn
  "Tails filename returned by newest-fn. Periodically checks is newest-fn
  returns a different filename. If so, stops previous tail and tails new file.

  If only is provided, filter output by grepping for that string."
  ([newest-fn] (tail-fn newest-fn nil))
  ([newest-fn only]
   (let [!proc (atom nil)]
     (try
       (loop [curr nil
              fname (newest-fn)]
         (when-not (= curr fname)
           (when-let [proc @!proc]
             (conch/destroy proc)
             (reset! !proc nil))
           (do
             (let [cmd (tail-cmd fname only)]
               (util/info "Now tailing %s...\n" fname)
               (reset! !proc (apply sh* cmd)))))
         (Thread/sleep 500)
         (recur fname (newest-fn)))
       (finally
         (when-let [proc @!proc]
           (conch/destroy proc)
           (reset! !proc nil)))))))
