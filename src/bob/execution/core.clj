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
  (:require [clojure.java.shell :refer [sh]]
            [manifold.deferred :refer [let-flow]]
            [failjure.core :as f]
            [bob.execution.internals :as b]
            [bob.util :refer [respond]]))

;; TODO: Extract the let-flow->s to a macro?

(defn start
  "Handler to start a single container by id.
  Synchronously starts the container and waits for completion.
  Returns the id of the container or error message if failed."
  [name]
  (let-flow [result (f/ok-> (b/run name))]
    (respond (if (f/failed? result)
               (f/message result)
               result))))

(defn logs-of
  "Handler to fetch logs from a running/dead container.
  Takes the id and the starting offset and the lines to read.
  Returns the list of logs or error message if failed."
  [name from count]
  (let-flow [result (f/ok-> (b/log-stream-of name)
                            (b/read-log-stream from count))]
    (respond (if (f/failed? result)
               (f/message result)
               result))))

(defn status-of
  "Handler to fetch status from a container.
  Returns the state and exit code (if any) of the container
  or error message if failed."
  [^String name]
  (let-flow [result (f/ok-> (b/status-of name))]
    (respond result)))

(defn cancel
  "Handler to cancel a running container by id.
  Returns Ok or error message if failed."
  [^String name]
  (let-flow [running? (-> (status-of name)
                          (:message)
                          (:running))
             result   (f/ok-> (if running?
                                (b/kill-container name)
                                name)
                              (b/remove-container))]
    (respond (if (f/failed? result)
               (f/message result)
               "Ok"))))

(defn gc
  "Handler to clean up resources.
  Removes all non running images and containers.
  WILL CAUSE BUILD HISTORY LOSS!"
  ([] (gc false))
  ([all]
   (let [base-args ["docker" "system" "prune" "-f"]
         args      (if all (conj base-args "-a") base-args)]
     (let-flow [result (f/ok-> (apply sh args))]
       (respond result)))))
