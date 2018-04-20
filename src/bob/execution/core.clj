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
  (:require [manifold.deferred :refer [let-flow]]
            [failjure.core :as f]
            [bob.execution.blocks :as b]
            [bob.util :refer [respond]]))

;; TODO: Extract the let-flow->s to a macro?

(defn start
  ([] (start b/default-command b/default-image))
  ([command] (start command b/default-image))
  ([command image]
   (let-flow [result (f/ok-> (b/pull image)
                             (b/build command)
                             (b/run))]
             (respond (if (f/failed? result)
                        (f/message result)
                        result)))))

(defn logs-of
  [name from count]
  (let-flow [result (f/ok-> (b/log-stream-of name)
                            (b/read-log-stream from count))]
            (respond (if (f/failed? result)
                       (f/message result)
                       result))))

(defn stop
  [^String name]
  (let-flow [result (f/ok-> (b/kill-container name)
                            (b/remove-container))]
            (respond (if (f/failed? result)
                       (f/message result)
                       "Ok"))))
