(ns mattsum.boot-react-native
  {:boot/export-tasks true}
  (:require [boot
             [core :as c :refer [deftask with-pre-wrap]]
             [util :as util]]
            [boot.from.backtick :refer [template]]
            [clojure.java.io :as io]
            [me.raynes.conch :refer [programs with-programs let-programs] :as sh]
            [mattsum.impl
             [boot-helpers :as bh :refer [exit-code find-file shell]]
             [goog-deps :refer [get-files-to-process setup-links-for-dependency-map]]]))

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
        output-dir (c/tmp-dir!) ; Create the output dir in outer context allows us to cache the compilation, which means we don't have to re-parse each file
        ]
    (with-pre-wrap fileset
      (let [get-hash-diff #(c/fileset-diff @previous-files % :hash)

            new-files     (->> fileset
                               get-hash-diff)
            deps-files    (or deps-files ["cljs_deps.js" "goog/deps.js"])
            src-dir       (str (or cljs-dir "main.out") "/")]
        (reset! previous-files fileset)

        (util/dbug "Compiling {cljs-deps}... %d changed files\n" (count new-files) )
        (let [files-to-process (get-files-to-process deps-files fileset output-dir src-dir)]
          (setup-links-for-dependency-map files-to-process))

        (util/dbug "Adding %s to fileset\n" output-dir)
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
  []
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
                                       nil
                                       ns
                                       temp))))

(deftask shim-repl-print
  "Weasel's repl-print function does not work in React Native
   TODO: Add PR for changing this function in Weasel code"
  []
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
                                       nil
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
     (link-goog-deps)
     (replace-main)
     ))

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

(deftask start-rn-packager
  "Starts the React Native packager. Includes a custom transformer that skips transformation for ClojureScript generated files."
  [a app-dir OUT str  "The (relative) path to the React Native application"
   t target-path TAR str "The (relative) path to the build directory (e.g. app/build)"]
  (let [app-dir (or app-dir "app")
        build-dir (or target-path (str app-dir "/build"))
        transformer-rel-path "transformer/cljs-rn-transformer.js"
        transformer-path (bh/write-resource-to-path
                          "mattsum/boot_rn/js/cljs-rn-transformer.js"
                          transformer-rel-path)
        command (str app-dir "/node_modules/react-native/packager/packager.sh --transformer " build-dir "/" transformer-rel-path)
        process (atom nil)]
    (comp
     (c/with-pre-wrap fileset
       (-> fileset
           (c/add-resource transformer-path)
           (c/commit!))
       )
     (c/with-post-wrap fileset
       (util/info "Starting React Packager - %s\n" command)
       (let [start-process #(reset! process (shell command))]
         (when (nil? @process)
           (start-process))
         (let [exit (exit-code @process)]
           (when (realized? exit) ;;restart server if necessary
             (if (= 0 @exit)
               (util/warn "Process exited normally, restarting.\n")
               (util/fail "Process crashed, restarting.\n"))
             (start-process))))
       fileset))))

(deftask shim-browser-repl-bootstrap
  "Prevents bootstrap from running twice and causing goog.require to loop indefinitely"
  []
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
                                       nil
                                       ns
                                       temp))))

(deftask before-cljsbuild
  []
  (comp
   (c/with-pre-wrap fileset
     (util/info "Boot React Native: shimming...\n")
     fileset)
   (shim-browser-repl-bootstrap)
   (shim-boot-reload)
   (shim-repl-print)
   ))

(deftask after-cljsbuild
  [o output-dir OUT str  "The cljs :output-dir"
   a asset-path PATH str "The (optional) asset-path. Path relative to React Native app where main.js is stored."
   s server-url SERVE str "The (optional) IP address and port for the websocket server to listen on."
   A app-dir OUT str  "The (relative) path to the React Native application"]
  (comp (c/with-pre-wrap fileset
          (util/info "Boot React Native: setting up dev environment...\n")
          fileset)
        (react-native-devenv :output-dir output-dir
                             :asset-path asset-path
                             :server-url server-url)))

(deftask run-in-simulator
  "Run the app in the simulator"
  []
  (let [running (atom false)]
    (c/with-post-wrap fileset
      (when-not @running ;; make sure we run only once
        (reset! running true)
        (binding [util/*sh-dir* "app"]
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
  [f files ORIGIN:TARGET {str str} "{origin target} pair of files to bundle"]
  (let  [tmp (c/tmp-dir!)]
    (c/with-pre-wrap fileset
      (doseq [[origin target] files]
        (let [in  (bh/file-by-path origin fileset)
              out (clojure.java.io/file tmp target)]
          (clojure.java.io/make-parents out)
          (bh/bundle* in out tmp)))
      (-> fileset (c/add-resource tmp) c/commit!))))
