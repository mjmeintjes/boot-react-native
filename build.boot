(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[
                 [pandeiro/boot-http             "0.7.1-SNAPSHOT"  :scope  "test"]
                 [crisptrutski/boot-cljs-test    "0.2.0-SNAPSHOT"  :scope  "test"]
                 [com.cemerick/url "0.1.1"]
                 [me.raynes/conch "0.8.0"]
                 ])

(def +version+ "0.0.3")

(task-options!
 pom {:project 'mattsum/boot-react-native
      :version +version+
      :description "Boot tasks to integrate ClojureScript boot tasks (reload, repl, cljs-build) with React Native packager"
      :url "https://github.com/mjmeintjes/boot-react-native"
      :scm {:url "https://github.com/mjmeintjes/boot-react-native"}
      :license {"Eclipse Public License"
                "http://www.eclipse.org/legal/epl-v10.html"}})
