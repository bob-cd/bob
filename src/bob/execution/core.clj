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
  (:require [clojure.string :refer [split-lines]]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [bob.execution.blocks :refer [docker default-image default-command log-params pull build run
                                          log-stream-of read-log-stream]]
            [bob.util :refer [m]]))

(defn start
  [_]
  (d/let-flow [result (f/ok-> (pull default-image)
                              (build default-command)
                              (run))]
              (m (if (f/failed? result)
                   (f/message result)
                   result))))

(defn logs-of
  [id count]
  (d/let-flow [result (f/ok-> (log-stream-of id)
                              (read-log-stream count))]
              (m (if (f/failed? result)
                   (f/message result)
                   result))))

(defn stop
  [^String id]
  (d/let-flow [_ (.killContainer docker id)
               _ (.removeContainer docker id)]
              (m true)))
