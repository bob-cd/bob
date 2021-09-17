;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

;; see https://ask.clojure.org/index.php/10905/control-transient-deps-that-compiled-assembled-into-uberjar?show=10913#c10913
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
                    :compile-opts ["-Dclojure.compiler.direct-linking=true"]})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      'runner.main})))
