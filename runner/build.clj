; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

;; see
;; https://ask.clojure.org/index.php/10905/control-transient-deps-that-compiled-assembled-into-uberjar?show=10913#c10913
(require 'clojure.tools.deps.alpha.util.s3-transporter)

(ns build
  (:require [clojure.tools.build.api :as b]))

(def uber-file "runner.jar")

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
                  :lib       'bob/runner
                  :version   "0.1.0"
                  :basis     basis
                  :src-dirs  src-dirs})
    (b/copy-dir {:src-dirs   (conj src-dirs "resources")
                 :target-dir class-dir})
    (b/compile-clj {:basis        basis
                    :src-dirs     src-dirs
                    :class-dir    class-dir
                    :ns-compile   '[runner.main]
                    :java-cmd     (or (System/getenv "JAVA_CMD") "java")
                    :java-opts    ["--enable-preview"]
                    :compile-opts {:direct-linking true}})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      'runner.main})))
