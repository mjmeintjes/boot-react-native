(set-env! :resource-paths #{"src" "resources"}
          :dependencies '[[pandeiro/boot-http "0.7.1-SNAPSHOT" :scope  "test"]
                          [com.cemerick/url "0.1.1"]
                          [me.raynes/conch "0.8.0"]
                          [org.clojure/clojure "1.8.0" :scope "provided"]]
          :repositories
          (partial map (fn [[k v]] [k (cond-> v (#{"clojars"} k) (assoc :username (System/getenv "CLOJARS_USER"),
                                                                        :password (System/getenv "CLOJARS_PASS")))])))

(def +version+ "0.3-rc2")

(task-options!
 pom {:project 'boot-react-native/boot-react-native
      :version +version+
      :description "Boot tasks to integrate ClojureScript boot tasks (reload, repl, cljs-build) with React Native packager"
      :url "https://github.com/mjmeintjes/boot-react-native"
      :scm {:url "https://github.com/mjmeintjes/boot-react-native"}
      :license {"Eclipse Public License"
                "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build []
  (comp (pom)
        (jar)
        (install)))

(deftask dev []
  "Continuously build jar and install to local maven repository"
  (comp (watch)
        (build)))

(deftask deploy []
  (comp (build)
        (push :repo "clojars"
              :gpg-sign false #_(not (.endsWith +version+ "-SNAPSHOT")))))
