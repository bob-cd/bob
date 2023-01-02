; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns build
  (:require [clojure.tools.build.api :as b]))

(def uber-file "apiserver.jar")

(defn clean
  [_]
  (b/delete {:path "target"})
  (b/delete {:path uber-file}))

(defn uber
  [_]
  (let [basis     (b/create-basis {:project "deps.edn"})
        class-dir "target/classes"
        src-dirs  ["src"]]
    (clean nil)
    (b/write-pom {:class-dir class-dir
                  :lib       'bob/apiserver
                  :version   "0.1.0"
                  :basis     basis
                  :src-dirs  src-dirs})
    (b/copy-dir {:src-dirs   (conj src-dirs "resources")
                 :target-dir class-dir})
    (b/compile-clj {:basis        basis
                    :src-dirs     src-dirs
                    :class-dir    class-dir
                    :ns-compile   '[apiserver.main]
                    :java-cmd     (or (System/getenv "JAVA_CMD") "java")
                    :java-opts    ["--enable-preview"]
                    :compile-opts {:direct-linking true}})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      'apiserver.main})))
