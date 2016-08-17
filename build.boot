(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[pandeiro/boot-http "0.7.1-SNAPSHOT"  :scope  "test"]
                 [com.cemerick/url "0.1.1"]
                 [me.raynes/conch "0.8.0"]])

(def +version+ "0.3-rc1")

(task-options!
 pom {:project 'boot-react-native/boot-react-native
      :version +version+
      :description "Boot tasks to integrate ClojureScript boot tasks (reload, repl, cljs-build) with React Native packager"
      :url "https://github.com/mjmeintjes/boot-react-native"
      :scm {:url "https://github.com/mjmeintjes/boot-react-native"}
      :license {"Eclipse Public License"
                "http://www.eclipse.org/legal/epl-v10.html"}})

(set-env! :repositories [["clojars" (cond-> {:url "https://clojars.org/repo/"}
                                      (System/getenv "CLOJARS_USER")
                                      (merge {:username (System/getenv "CLOJARS_USER")
                                              :password (System/getenv "CLOJARS_PASS")}))]])

(deftask build []
  (comp
   (pom) (jar) (install)))

(deftask dev []
  "Continuously build jar and install to local maven repository"
  (comp (watch)
        (build)))

(deftask deploy []
  (comp (build)
        (push :repo "clojars"
              :gpg-sign false #_(not (.endsWith +version+ "-SNAPSHOT")))))
