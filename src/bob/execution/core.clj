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
            [bob.execution.blocks :as b]
            [bob.util :refer [m]]))

(defn start
  [_]
  (d/let-flow [result (f/ok-> (b/pull b/default-image)
                              (b/build b/default-command)
                              (b/run))]
              (m (if (f/failed? result)
                   (f/message result)
                   result))))

(defn logs-of
  [name count]
  (d/let-flow [result (f/ok-> (b/log-stream-of name)
                              (b/read-log-stream count))]
              (m (if (f/failed? result)
                   (f/message result)
                   result))))

(defn stop
  [^String name]
  (d/let-flow [result (f/ok-> (b/kill-container name)
                              (b/remove-container))]
              (m (if (f/failed? result)
                   (f/message result)
                   true))))
