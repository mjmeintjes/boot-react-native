(set-env!
 :source-paths   #{"src" "react-support"}
 :resource-paths   #{"resources"}
 :exclusions ['cljsjs/react]
 :dependencies '[
                 [mattsum/boot-react-native "0.1.1-SNAPSHOT" :scope "test"]
                 [adzerk/boot-cljs               "1.7.170-3"       :scope  "test"]
                 [adzerk/boot-cljs-repl          "0.3.0"           :scope  "test"]
                 [adzerk/boot-reload             "0.4.2"           :scope  "test"]
                 [pandeiro/boot-http             "0.7.1-SNAPSHOT"  :scope  "test"]
                 [crisptrutski/boot-cljs-test    "0.2.1-SNAPSHOT"  :scope  "test"]
                 [com.cemerick/piggieback        "0.2.1"           :scope  "test"]
                 [weasel                         "0.7.0"           :scope  "test"]
                 [org.clojure/tools.nrepl        "0.2.12"          :scope  "test"]
                 [org.clojure/clojure            "1.7.0"]
                 [org.clojure/clojurescript      "1.7.170"]
                 [reagent                        "0.5.1"]
                 ;; [react-native-externs "0.0.1-SNAPSHOT"]
                 ]
 )

(require
 '[adzerk.boot-cljs             :refer  [cljs]]
 '[adzerk.boot-cljs-repl        :refer  [cljs-repl  start-repl]]
 '[adzerk.boot-reload           :refer  [reload]]
 '[crisptrutski.boot-cljs-test  :as test :refer  [test-cljs]]
 '[pandeiro.boot-http           :refer  [serve]]
 '[boot.core                    :as     b]
 '[boot.util                    :as     u]
 '[clojure.string               :as     s]
 '[mattsum.boot-react-native    :as     rn]
 )

(deftask dev
  "Build app and watch for changes"
  [p platform PLATFORM kw "The platform to target (ios or android)"]
  []
  (assert (or (nil? platform) (#{:ios :android} platform)))
  (comp (watch)
        (if (= :ios platform) (rn/run-in-simulator) identity)
        (reload :on-jsload 'mattsum.simple-example.core/on-js-reload
                :port 8079
                :ws-host "localhost"
                )
        (rn/before-cljsbuild)

        (cljs-repl :ws-host "localhost"
                   :port 9001
                   :ip "0.0.0.0")

        (cljs :main "mattsum.simple-example.core")
        (rn/after-cljsbuild :server-url "localhost:8081")
        (if (= :ios platform) (rn/print-ios-log :grep "SimpleExampleApp") identity)
        (if (= :android platform) (rn/print-android-log) identity)
        (target :dir ["app/build"])
        ))

(deftask packager
  []
  (watch)
  (rn/start-rn-packager))

(defn ^:private file-by-path [path fileset]
  (b/tmp-file (get (:tree fileset) path)))

(defn bundle* [in out]
  (let [tempfname "nested/temp/temp.js"
        cli (-> "app/node_modules/react-native/local-cli/cli.js"
                java.io.File.
                .getAbsolutePath)
        dir (-> in .getAbsoluteFile .getParent)
        fname (-> in .getAbsolutePath)]
    (binding [u/*sh-dir* "app"]
      ;; TODO: generate temp file name here
      (u/dosh "cp" fname tempfname)
      (u/dosh "node" cli
              "bundle" "--platform" "ios"
              "--dev" "true"
              "--entry-file" tempfname
              "--bundle-output" (.getAbsolutePath out))
      (u/dosh "rm" "-f" tempfname))))

(deftask bundle
  "Bundle the files specified"
  [f files ORIGIN:TARGET {str str} "{origin target} pair of files to bundle"]
  (let  [tmp (b/tmp-dir!)]
    (b/with-pre-wrap fileset
      (u/dbug "File mapping: %s\n" (pr-str files))
      (doseq [[origin target] files]
        (let [in  (file-by-path origin fileset)
              out (clojure.java.io/file tmp target)]
          (clojure.java.io/make-parents out)
          (bundle* in out)))
      (-> fileset (b/add-resource tmp) b/commit!))))

(deftask dist
  "Build a distributable bundle of the app"
  []
  (comp
   (cljs :ids #{"main"})
   (bundle :files {"main.js" "main.jsbundle"})
   (target :dir ["app/dist"])))
