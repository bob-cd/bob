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
                 [compojure "1.6.0"]
                 [com.fasterxml.jackson.core/jackson-core "2.9.5"]
                 [com.spotify/docker-client "8.11.2"]
                 [failjure "1.3.0"]]
  :plugins [[lein-cloverage "1.0.10"]
            [lein-ancient "0.6.15"]]
  :jvm-opts ["--add-modules" "java.xml.bind"]
  :global-vars {*warn-on-reflection* true}
  :main bob.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
