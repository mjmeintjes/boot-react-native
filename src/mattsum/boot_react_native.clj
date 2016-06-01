(ns mattsum.boot-react-native
  {:boot/export-tasks true}
  (:require [boot
             [core :as c :refer [deftask with-pre-wrap]]
             [util :as util]
             [file :as bf]]
            [boot.from.backtick :refer [template]]
            [clojure.java.io :as io]
            [me.raynes.conch :refer [programs with-programs let-programs] :as sh]
            [mattsum.impl
             [boot-helpers :as bh :refer [exit-code find-file shell]]
             [goog-deps :refer [get-files-to-process setup-links-for-dependency-map]]]
            [clojure.string :as str]))

;;(use 'alex-and-georges.debug-repl)

(deftask link-goog-deps
  "Parses Google Closure deps files, and creates a link in the output node_modules directory
   to each file.

   This makes it possible for the React Native packager to pick up the dependencies when building
   the JavaScript Bundle allowing us to develop with :optimizations :none.
   
   Explanation:
   In order for `:optimizations :none` to work (where cljs just spits out individual files),
   we have to let React Native know about those files. Luckily, RN packager picks up `goog.require`
   just like it picks up `require`. Unluckily, `goog.require` does not map to a specific file. Rather,
   Google Closure maps the module name provided in `goog.require` to a file name via mappings set up
   in deps.js files.
   
   Because we are short-circuiting `goog.require` in order to work with RN, we need to short-circuit
   the deps lookup as well. This task looks up the filename for each cljs module, and then renames
   that file (using the module name expected by `require`), and places the file in `node_modules`,
   because that is where React Native expects it to be."
  [d deps-files DEPS #{str}  "A list of relative paths to deps files to parse"
   o cljs-dir OUT str  "The cljs :output-dir"]
  (let [previous-files (atom nil)
        ;; Create the output dir in outer context allows us to cache the
        ;; compilation, which means we don't have to re-parse each file
        tmp-dir        (c/tmp-dir!)
        cljs-dir (or cljs-dir "main.out")
        output-dir     (if cljs-dir
                         (doto (io/file tmp-dir cljs-dir)
                           io/make-parents)
                         tmp-dir)]
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

        (util/info "Adding %s to fileset\n" tmp-dir)
        (-> fileset
            (c/add-resource tmp-dir)
            c/commit!)))))



(deftask replace-main
  "Replaces the main.js with a file that can be read by React Native's packager"
  [o output-dir OUT str  "The cljs :output-dir"]
  (let []
    (with-pre-wrap fileset
      (let [out-dir (str (or output-dir "main.out") "/")
            tmp (c/tmp-dir!)
            main-file (->> (str out-dir "cljs_deps.js")
                           (find-file fileset))
            boot-main (->> main-file
                           slurp
                           (re-find #"'(boot.cljs.\w+)'"))
            boot-main (get boot-main 1)
            out-file (doto (io/file tmp out-dir "main.js")
                       (io/make-parents))
            new-script (str "
var CLOSURE_UNCOMPILED_DEFINES = null;
require('./goog/base.js');
require('" boot-main "');
")]
        (spit out-file new-script)
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))


(deftask add-resource-to-output
  "Appends text in specified resource path to goog/base.js"
  [o output-dir OUT str  "The cljs :output-dir"
   r resource-path RES str "Path to resource to append"
   j output-file FIL str "Output file to append to"
   p replacements REP edn "List of replacements to make in resource file (for basic templating, e.g. [[\"var1\" \"VALUE1\"}]] will replace var1 with VALUE1 in output)"
   a action ACT kw "Where to insert resource"]

  (let [modify-fn (if (= action :prepend)
                    bh/prepend-to-file
                    bh/append-to-file)]
    (with-pre-wrap fileset
      (-> fileset
          (bh/add-resource-to-file (bh/output-file-path output-dir output-file) resource-path (or replacements []) modify-fn)
          c/commit!))))

(deftask shim-goog-reloading
  "Appends some javascript to goog/base.js in order for boot-reload to work automatically"
  [o output-dir OUT str  "The cljs :output-dir"
   a asset-path PATH str "The (optional) asset-path. Path relative to React Native app where main.js is stored."
   s server-url SERVE str "The (optional) IP address and port for the websocket server to listen on."]
  (add-resource-to-output :output-dir output-dir
                             :resource-path "mattsum/boot_rn/js/reloading.js"
                             :output-file "goog/net/jsloader.js"
                             :replacements [["{{ asset-path }}" (str "/" (or asset-path "build"))]
                                            ["{{ server-url }}" (str "http://" (or server-url "localhost:8081"))]]))

(deftask shim-goog-req
  "Appends some javascript code to goog/base.js in order for React Native to work with Google Closure files"
  [o output-dir OUT str  "The cljs :output-dir"]
  (comp  (add-resource-to-output :output-dir output-dir
                                 :resource-path "mattsum/boot_rn/js/goog_base.js"
                                 :output-file "goog/base.js")
      (add-resource-to-output :output-dir output-dir
                                 :resource-path "mattsum/boot_rn/js/goog_base_prepend.js"
                                 :output-file "goog/base.js"
                                 :action :prepend)))


(deftask shim-boot-reload
  [i id ID str "The CLJS build ID"]
  (let [ns 'mattsum.boot-react-native.shim-boot-reload
        temp (template
              ((ns ~ns
                 (:require [adzerk.boot-reload.display :as display]
                           [adzerk.boot-reload.reload :as reload]))
               (let [no-op (fn [& args] ())
                     pr (fn [& args] (println args))]
                 (aset js/adzerk.boot_reload.display "display" pr)
                 (aset js/adzerk.boot_reload.reload "reload_html" no-op)
                 (aset js/adzerk.boot_reload.reload "reload_css" no-op)
                 (aset js/adzerk.boot_reload.reload "reload_img" no-op))))]
    (c/with-pre-wrap fileset
      (bh/add-cljs-template-to-fileset fileset
                                       (when id #{id})
                                       ns
                                       temp))))

(deftask shim-repl-print
  "Weasel's repl-print function does not work in React Native
   TODO: Add PR for changing this function in Weasel code"
  [i id ID str "The CLJS build ID"]
  (let [ns 'mattsum.boot-react-native.shim-repl-print
        temp (template
              ((ns ~ns
                 (:require [weasel.repl :as repl]
                           [clojure.browser.net :as net]))
               (aset js/weasel.repl "repl_print"
                     (fn [& args]
                       (when-let [conn @repl/ws-connection]
                         (.apply (.-log js/console) js/console (into-array args))
                         (net/transmit @repl/ws-connection
                                       (pr-str {:op :print :value (apply pr-str args)})))))
               ))]
    (c/with-pre-wrap fileset
      (bh/add-cljs-template-to-fileset fileset
                                       (when id #{id})
                                       ns
                                       temp))))

(deftask react-native-devenv
  [o output-dir OUT str  "The cljs :output-dir"
   a asset-path PATH str "The (optional) asset-path. Path relative to React Native app where main.js is stored."
   s server-url SERVE str "The (optional) IP address and port for the websocket server to listen on."]

  (comp (shim-goog-req :output-dir output-dir)
        (shim-goog-reloading :output-dir output-dir
                             :asset-path asset-path
                             :server-url server-url)
        (link-goog-deps :cljs-dir output-dir)
        (replace-main :output-dir output-dir)))

(deftask print-android-log
  "Prints React Native log messages (from adb logcat)"
  []
  ;; TODO: support different log levels
  (let [log-process (atom nil)
        process-line (fn [line _]
                       ;; TODO: parse time from line, and only display new logs (time after startup - problem is that time on phone might differ from local time)
                       ;; TODO: colorize output - see https://github.com/cesarferreira/react-native-logcat/blob/master/lib/react-native-logcat.rb
                       ;; TODO: Support console.group? - will need support from JS side as well
                       (println line))]
    (c/with-pre-wrap fileset
      (with-programs [adb]
        (when (nil? @log-process)
          (reset! log-process
                  (future (adb "logcat" "-v" "time" "*:S" "ReactNative:V" "ReactNativeJS:V"
                               {:out process-line}))))
        )
      fileset)))

(def ^:private rn-transformer-relpath "cljs-rn-transformer.js")

(defn- setup-rn-work-dir
  "Given that the React Native application lives in `app-dir`, and given a clean boot `tmp-dir!` as
  `work-dir`, copy the app's node_modules across to `tmp-dir` so that we can run React Native
  scripts in the directory structure they expect to be run in, without polluting the `app-dir`, and
  without using the boot fileset (since unfortunately node_modules contains too many thousands of
  files for boot's filesystem watch mechanism to be able to cope with).

  Note that this does *not* need to be run for each iteration of a watched pipeline: it only has to
  happen once as it does not interact in any way with the boot fileset."
  [app-dir work-dir]
  (let [tx-file (io/file work-dir rn-transformer-relpath)]
    (util/info "Writing %s.\n" (.toPath tx-file))
    (spit tx-file (slurp (io/resource "mattsum/boot_rn/js/cljs-rn-transformer.js"))))

  (let [modules-dir  (io/file (str app-dir "/node_modules"))
        modules-path (.toPath modules-dir)]
    (util/info "Copying modules in %s to %s.\n"
               modules-path
               (str (.getAbsolutePath work-dir) "/node_modules"))
    (doseq [in-file (bf/file-seq modules-dir)]
      (when-not (.isDirectory in-file)
        (let [relpath  (str (.relativize modules-path (.toPath in-file)))
              out-file (io/file work-dir "node_modules" relpath)]
          (io/make-parents out-file)
          (bf/hard-link in-file out-file))))))

(defn- sync-code-to-rn-work-dir
  "Syncs the code output by the boot task pipeline from the fileset-diff to the React Native working
  directory for we've previously setup with `setup-rn-work-dir`, so that our code appears to React
  Native in the directory structure it expects, even as it's continually recompiled in a `watch`ed
  task pipeline."
  [output-dir work-dir fileset-diff]
  (let [output-dir (or output-dir "main.out")]
    (when-let [tmp-files (seq (c/by-re [(re-pattern (str "^\\Q" output-dir "\\E/main.js$"))
                                        (re-pattern (str "^\\Q" output-dir "\\E/goog/base.js$"))
                                        (re-pattern (str "^\\Q" output-dir "\\E/goog/net/jsloader.js$"))
                                        (re-pattern (str "^\\Q" output-dir "\\E/node_modules/.*"))]
                                       (c/output-files fileset-diff)))]
      (util/info "Copying %d output files to React Native packager working directory %s.\n"
                 (count tmp-files) work-dir)
      (doseq [tmp-file tmp-files]
        (let [in-file  (c/tmp-file tmp-file)
              relpath  (c/tmp-path tmp-file)
              out-file (io/file work-dir (str/replace relpath (str output-dir "/") ""))]
          (util/dbug "... copying %s\n" in-file)
          (io/make-parents out-file)
          (bf/hard-link in-file out-file))))))

(deftask start-rn-packager
  "Starts the React Native packager. Includes a custom transformer that skips transformation for ClojureScript generated files."
  [a app-dir APP str "The (relative) path to the React Native application"
   o output-dir OUT str "The cljs :output-dir"]
  (let [output-dir  (or output-dir "main.out")
        app-dir     (or app-dir "app")
        rn-work-dir (c/tmp-dir!)]
    (setup-rn-work-dir app-dir rn-work-dir)

    (comp (let [previous-files (atom nil)]
            (c/with-post-wrap fileset
              (let [fileset-diff (c/fileset-diff @previous-files fileset :hash)]
                (reset! previous-files fileset)
                (sync-code-to-rn-work-dir output-dir rn-work-dir fileset-diff))))

          (let [command       ["node_modules/react-native/packager/packager.sh"
                               "--transformer" rn-transformer-relpath]
                process       (atom nil)
                start-process #(reset! process (binding [util/*sh-dir* rn-work-dir]
                                                 (apply bh/sh* command)))]
            (c/with-post-wrap fileset
              (when (nil? @process)
                (util/info "Starting React Packager - %s\n" (str/join " " command))
                (start-process))
              (let [exit (exit-code @process)]
                (when (realized? exit) ;;restart server if necessary
                  (if (= 0 @exit)
                    (util/warn "Process exited normally, restarting.\n")
                    (util/fail "Process crashed, restarting.\n"))
                  (start-process))))))))

(deftask shim-browser-repl-bootstrap
  "Prevents bootstrap from running twice and causing goog.require to loop indefinitely"
  [i id ID str "The CLJS build ID"]
  (let [ns 'mattsum.boot-react-native.shim-browser-repl-bootstrap
        temp (template
              ((ns ~ns
                 (:require [clojure.browser.repl :as repl]))
               (defonce orig-bootstrap repl/bootstrap)
               (aset js/clojure.browser.repl "bootstrap"
                     (fn []
                       (when (.-require__ js/goog)
                         (set! js/goog.require (.-require__ js/goog))
                         (orig-bootstrap))
                       ))
               ))]
    (c/with-pre-wrap fileset
      (bh/add-cljs-template-to-fileset fileset
                                       (when id #{id})
                                       ns
                                       temp))))

(deftask before-cljsbuild
  [i id ID str "The CLJS build ID"]
  (comp (shim-browser-repl-bootstrap :id id)
        (shim-boot-reload :id id)
        (shim-repl-print :id id)))

(deftask after-cljsbuild
  [o output-dir OUT str  "The cljs :output-dir"
   a asset-path PATH str "The (optional) asset-path. Path relative to React Native app where main.js is stored."
   s server-url SERVE str "The (optional) IP address and port for the websocket server to listen on."
   A app-dir OUT str  "The (relative) path to the React Native application"]
  (comp (react-native-devenv :output-dir output-dir
                             :asset-path asset-path
                             :server-url server-url)
        (start-rn-packager :app-dir app-dir
                           :output-dir output-dir)))

(deftask run-in-simulator
  "Run the app in the simulator"
  [A app-dir OUT str  "The (relative) path to the React Native application"]
  (let [running (atom false)]
    (c/with-post-wrap fileset
      (when-not @running ;; make sure we run only once
        (reset! running true)
        (binding [util/*sh-dir* (or app-dir "app")]
          (util/dosh "node" "node_modules/react-native/local-cli/cli.js" "run-ios")))
      fileset)))

(deftask print-ios-log
  "Print iOS simulator log"
  [g grep GREP str "Only print lines containg GREP, using fgrep(1). Defaults to printing all lines"]
  (let [!running (atom false)]
    (c/with-pre-wrap fileset
      (when-not @!running ;; make sure we run only once
        (reset! !running true)
        (future (bh/tail-fn bh/newest-log grep)))
      fileset)))

(deftask bundle
  "Bundle the files specified"
  [a app-dir  APP           str       "The (relative) path to the React Native application"
   c cljs-dir CLJS          str       "The (relative) path to the compiled CLJS code in the fileset"
   p platform PLATFORM      str       "The platform to build for (defaults to 'ios')"
   P prod                   bool      "True to build a dev=false build"
   f files    ORIGIN:TARGET {str str} "{origin target} pair of files to bundle"]
  (let [platform       (or platform "ios")
        rn-work-dir    (c/tmp-dir!)
        bundle-dir     (c/tmp-dir!)
        previous-files (atom nil)]
    (setup-rn-work-dir app-dir rn-work-dir)

    (comp (shim-goog-req :output-dir cljs-dir)
          (link-goog-deps :cljs-dir cljs-dir)
          (replace-main :output-dir cljs-dir)

          (c/with-pre-wrap fileset
            (let [fileset-diff (c/fileset-diff @previous-files fileset :hash)]
              (reset! previous-files fileset)
              (sync-code-to-rn-work-dir cljs-dir rn-work-dir fileset-diff))

            (doseq [[origin target] files]
              (let [in   (io/file rn-work-dir origin)
                    out  (io/file bundle-dir target)
                    outd (.getParent (.getAbsoluteFile out))]
                (io/make-parents out)

                (let [command ["node" "node_modules/react-native/local-cli/cli.js"
                               "bundle"
                               "--platform" platform
                               "--dev" (str (boolean (not prod)))
                               "--entry-file" origin
                               "--transformer" (.getAbsolutePath
                                                (io/file rn-work-dir rn-transformer-relpath))
                               "--bundle-output" (.getAbsolutePath out)
                               "--sourcemap-output" (str (.getAbsolutePath out) ".map")
                               "--assets-dest" outd]]
                  (util/info "Running React Native bundle - %s\n" (str/join " " command))
                  (binding [util/*sh-dir* rn-work-dir]
                    (apply util/dosh command)))))

            (-> fileset (c/add-resource bundle-dir) c/commit!)))))
