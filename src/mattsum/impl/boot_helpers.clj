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

(defn read-resource
  [src]
  (println "Reading resource - " src)
  (->> src io/resource slurp))

(defn make-absolute
  "Makes a relative path absolute using the provided tmp dir"
  [tmp rel-path]
  (str tmp "/" rel-path))

(defn not-nil
  [msg obj]
  (when (nil? obj)
    (throw (ex-info msg)))
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
           ))))
)

(defn append-to-file
  [fileset path content replacements]
  (modify-file fileset path #(str % "\n" content) replacements))

(defn append-resource-to-file
  "Reads a resource file stored in the jar, and appends it to file in 'path'"
  [fileset path resource-path replacements]
  (let [new-script (read-resource resource-path)]
    (append-to-file fileset path new-script replacements)
    ))

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
    (util/info "Writing " resource-path " to " out-file)
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
    (util/info "Writing %s...\n" (.getName out-file))
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
    (util/info "Found edn files - " (get-cljs-edn-files fileset ids))
    (util/info "RUNNING")
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

(defn kill [process]
  (when-not (nil? process)
    (conch/destroy process)))

(defn exit-code [process]
  (future (conch/exit-code process)))
