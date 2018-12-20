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

(ns bob.execution.core
  (:require [clojure.java.shell :as shell]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [bob.util :as u]))

(defn gc
  "Handler to clean up resources.
  Removes all non running images and containers.
  WILL CAUSE BUILD HISTORY LOSS!"
  ([] (gc false))
  ([all]
   (let [base-args ["docker" "system" "prune" "-f"]
         args      (if all (conj base-args "-a") base-args)]
     (d/let-flow [result (f/ok-> (apply shell/sh args))]
       (u/respond (if (f/failed? result)
                    (f/message result)
                    "Ok"))))))
