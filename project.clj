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

(defproject bob "0.1.0"
  :description "This is what CI/CD should've been."
  :license {:name "GPL 3.0"
            :url  "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [manifold "0.1.6"]
                 [aleph "0.4.4"]
                 [ring "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [compojure "1.6.1"]
                 [com.fasterxml.jackson.core/jackson-core "2.9.5"]
                 [com.spotify/docker-client "8.11.2"]
                 [failjure "1.3.0"]
                 [com.h2database/h2 "1.4.197"]
                 [ragtime "0.7.2"]
                 [korma "0.5.0-RC1"]
                 [javax.xml.bind/jaxb-api "2.3.0"]          ;; For Aleph's XML dependency, Java 8 compat
                 [io.netty/netty-all "4.1.24.Final"]        ;; Forced Netty version for Java 9+ compat
                 [javax.activation/activation "1.1.1"]      ;; Java 9+ compat for XML bindings
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]]
  :plugins [[lein-ancient "0.6.15"]]
  :java-source-paths ["src/bob/java"]
  :global-vars {*warn-on-reflection* true}
  :main bob.main
  :target-path "target/%s"
  :profiles {:dev     {:dependencies [[org.clojure/test.check "0.9.0"]]}
             :uberjar {:aot :all}})
