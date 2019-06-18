;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(def project 'bob)

(def version "0.1.0")

(set! *warn-on-reflection* true)

(set-env! :resource-paths #{"resources"}
          :source-paths   #{"src"}
          :dependencies   '[[org.clojure/clojure "1.10.1"]
                            [org.clojure/core.async "0.4.500"]
                            [manifold "0.1.8"]
                            [aleph "0.4.6"]
                            [ring "1.7.1"]
                            [ring/ring-json "0.4.0"]
                            [compojure "1.6.1"]
                            [com.fasterxml.jackson.core/jackson-core "2.9.9"]
                            [failjure "1.3.0"]
                            [com.h2database/h2 "1.4.199"]
                            [ragtime "0.8.0"]
                            [korma "0.5.0-RC1"]
                            [hikari-cp "2.7.1"]
                            [metosin/compojure-api "2.0.0-alpha30"]
                            [prismatic/schema "1.1.11"]
                            [lispyclouds/clj-docker-client "0.2.3"]
                            [mount "0.1.16"]
                            [javax.xml.bind/jaxb-api "2.3.0"]           ;; For Aleph's XML dependency, Java 8 compat
                            [io.netty/netty-all "4.1.36.Final"]         ;; Forced Netty version for Java 9+ compat
                            [javax.activation/activation "1.1.1"]       ;; Java 9+ compat for XML bindings
                            [ch.qos.logback/logback-classic "1.2.3"]    ;; For sane logging defaults
                            ;; Test
                            [lambdaisland/kaocha-boot "0.0-14" :scope "test"]
                            [org.clojure/test.check "0.9.0" :scope "test"]
                            ;; Plugins
                            [boot-deps "0.1.9" :scope "test"]])

(task-options!
 aot {:all true}
 pom {:project     project
      :version     version
      :description "This is what CI/CD should've been."
      :url         "https://bob-cd.readthedocs.io/en/latest/"
      :scm         {:url "https://github.com/bob-cd/bob"}
      :license     {"GPL 3.0"
                    "https://www.gnu.org/licenses/gpl-3.0.en.html"}}
 repl {:init-ns    'bob.main}
 jar  {:main       'bob.main
       :file       (str project "-standalone.jar")})

(def compiler-opts
  {:direct-linking true
   :elide-meta     [:doc :file :line :added]})

(deftask build
  [d dir PATH #{str} "Directories to write to (target)."]
  (binding [clojure.core/*compiler-options* compiler-opts]
    (let [dir (if (seq dir) dir #{"target"})]
      (comp (aot)
            (pom)
            (uber)
            (jar)
            (target :dir dir)))))

(deftask run
  [a args ARG [str] "CLI args to Bob."]
  (require '[bob.main :as app])
  (apply (resolve 'app/-main) args)
  (wait))

(require '[kaocha.boot-task :refer [kaocha]])
