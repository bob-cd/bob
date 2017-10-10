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
  :dependencies [[org.clojure/clojure "1.9.0-beta2"]
                 [io.pedestal/pedestal.service "0.5.3"]
                 [io.pedestal/pedestal.route "0.5.3"]
                 [io.pedestal/pedestal.immutant "0.5.3"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [cheshire "5.8.0"]
                 [ragtime "0.7.2"]
                 [com.h2database/h2 "1.4.196"]
                 [korma "0.4.3"]]
  :plugins [[lein-cloverage "1.0.9"]
            [lein-ancient "0.6.10"]]
  :main bob.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
