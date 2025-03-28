; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns build
  (:require [clojure.data.json :as json]
            [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.uber :as uber]))

(def uber-file "apiserver.jar")

(defn clean
  [_]
  (b/delete {:path "target"})
  (b/delete {:path uber-file}))

(defn append-json
  [{:keys [path in existing _state]}]
  {:write
   {path
    {:append false
     :string
     (json/write-str
      (concat (json/read-str (slurp existing))
              (json/read-str (#'uber/stream->string in))))}}})

(defn uber
  [_]
  (let [basis (b/create-basis {:project "deps.edn"})
        class-dir "target/classes"
        src-dirs ["src"]]
    (clean nil)
    (b/write-pom {:class-dir class-dir
                  :lib 'bob/apiserver
                  :version "0.1.0"
                  :basis basis
                  :src-dirs src-dirs})
    (b/copy-dir {:src-dirs (conj src-dirs "resources")
                 :target-dir class-dir})
    (b/compile-clj {:basis basis
                    :src-dirs src-dirs
                    :class-dir class-dir
                    :ns-compile '[apiserver.main]
                    :java-cmd (or (System/getenv "JAVA_CMD") "java")
                    :java-opts ["--enable-preview"]
                    :compile-opts {:direct-linking true}})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main 'apiserver.main
             ;; See: https://github.com/mpenet/hirundo?tab=readme-ov-file#building-uberjars-with-hirundo
             :conflict-handlers {"META-INF/helidon/service.loader" :append-dedupe
                                 "META-INF/helidon/feature-metadata.properties" :append-dedupe
                                 "META-INF/helidon/config-metadata.json" append-json
                                 "META-INF/helidon/service-registry.json" append-json}})))
