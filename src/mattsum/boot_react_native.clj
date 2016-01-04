(ns mattsum.boot-react-native
  {:boot/export-tasks true}
  (:require [boot
             [core :as c :refer [deftask with-pre-wrap]]
             [util :as util]]
            [boot.from.backtick :refer [template]]
            [clojure.java.io :as io]
            [mattsum.impl
             [boot-helpers :as bh :refer [exit-code find-file shell]]
             [goog-deps :refer [get-files-to-process setup-links-for-dependency-map]]]))

;;(use 'alex-and-georges.debug-repl)

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


(deftask append-resource-to-output
  "Appends text in specified resource path to goog/base.js"
  [o output-dir OUT str  "The cljs :output-dir"
   r resource-path RES str "Path to resource to append"
   j output-file FIL str "Output file to append to"
   p replacements REP edn "List of replacements to make in resource file (for basic templating, e.g. [[\"var1\" \"VALUE1\"}]] will replace var1 with VALUE1 in output)"]
  (with-pre-wrap fileset
    (-> fileset
        (bh/append-resource-to-file (bh/output-file-path output-dir output-file) resource-path (or replacements []))
        c/commit!)))

(deftask shim-goog-reloading
  "Appends some javascript to goog/base.js in order for boot-reload to work automatically"
  [o output-dir OUT str  "The cljs :output-dir"
   a asset-path PATH str "The (optional) asset-path. Path relative to React Native app where main.js is stored."
   s server-url SERVE str "The (optional) IP address and port for the websocket server to listen on."]
  (append-resource-to-output :output-dir output-dir
                             :resource-path "mattsum/boot_rn/js/reloading.js"
                             :output-file "goog/net/jsloader.js"
                             :replacements [["{{ asset-path }}" (str "/" (or asset-path "build"))]
                                            ["{{ server-url }}" (str "http://" (or server-url "localhost:8081"))]]))

(deftask shim-goog-req
  "Appends some javascript code to goog/base.js in order for React Native to work with Google Closure files"
  [o output-dir OUT str  "The cljs :output-dir"]
  (comp  (append-resource-to-output :output-dir output-dir
                                 :resource-path "mattsum/boot_rn/js/goog_base.js"
                                 :output-file "goog/base.js")))


(deftask shim-boot-reload
  []
  ;; TODO: this should run on ClojureScript side
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

(deftask start-rn-packager
  "Starts the React Native packager. Includes a custom transformer that skips transformation for ClojureScript generated files."
  [a app-dir OUT str  "The (relative) path to the React Native application"]
  (let [app-dir (or app-dir "app")
        build-dir (c/get-env :target-path)
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
       (println "Starting React Packager - " command)
       (let [start-process #(reset! process (shell command))]
         (when (nil? @process)
           (start-process))
         (let [exit (exit-code @process)]
           (when (realized? exit) ;;restart server if necessary
             (if (= 0 @exit)
               (util/warn "Process exited normally, restarting.")
               (util/fail "Process crashed, restarting."))
             (start-process))))
       fileset))))

(deftask before-cljsbuild
  []
  (comp (shim-boot-reload)))

(deftask after-cljsbuild
  [o output-dir OUT str  "The cljs :output-dir"
   a asset-path PATH str "The (optional) asset-path. Path relative to React Native app where main.js is stored."
   s server-url SERVE str "The (optional) IP address and port for the websocket server to listen on."]
  (comp (react-native-devenv :output-dir output-dir
                          :asset-path asset-path
                          :server-url server-url)
     (start-rn-packager)))
