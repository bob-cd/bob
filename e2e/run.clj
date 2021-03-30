#!/usr/bin/env bb

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

(require '[clojure.test :as t]
         '[babashka.classpath :as cp]
         '[babashka.wait :as wait])

(println "Waiting for bob to start up on localhost:7777")
(wait/wait-for-port "localhost" 7777)
(println "Found bob")

(cp/add-classpath "end-to-end-tests")

(require 'tests)
(println "Running tests")
(def test-results
  (t/run-tests 'tests))

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))

(System/exit failures-and-errors)
