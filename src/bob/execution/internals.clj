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

(ns bob.execution.internals
  (:require [failjure.core :as f]
            [bob.util :as u]
            [clj-docker-client.core :as docker]))

(defonce conn (docker/connect))

(defn- has-image
  "Checks if an image is present locally.
  Returns the name or the error if any."
  [name]
  (let [result (u/unsafe! (filter #(= (-> % :repo-tags)
                                      [name]) (docker/image-ls conn)))]
    (if (or (f/failed? result) (zero? (count result)))
      (f/fail "Failed to find %s" name)
      name)))

(defn kill-container
  "Kills a running container using SIGKILL.
  Returns the name or the error if any."
  [name]
  (if (f/failed? (u/unsafe! (docker/kill conn name)))
    (f/fail "Could not kill %s" name)
    name))

(defn pull
  "Pulls in an image if it's not present locally.
  Returns the name or the error if any."
  [name]
  (if (and (f/failed? (has-image name))
           (f/failed? (u/unsafe! (do (println (format "Pulling %s" name))
                                     (docker/pull conn name)
                                     (println (format "Pulled %s" name))))))
    (f/fail "Cannot pull %s" name)
    name))

(defn build
  "Builds a container.
  Takes the base image and the entry point command.
  Returns the id of the built container."
  [^String image ^String cmd evars]
  (u/unsafe! (docker/create conn image cmd evars {})))

(defn status-of
  "Returns the status of a container by id."
  [^String id]
  (let [result (u/unsafe! (docker/container-state conn id))]
    (if (f/failed? result)
      (f/message result)
      {:running?  (:running? result)
       :exit-code (:exit-code result)})))

(defn run
  "Synchronously starts up a previously built container.
  Returns the id when complete or and error in case on non-zero exit."
  [^String id]
  (f/attempt-all [_      (u/unsafe! (docker/start conn id))
                  status (u/unsafe! (docker/wait-container conn id))]
    (if (zero? status)
      (u/format-id id)
      (f/fail "Abnormal exit."))
    (f/when-failed [err] err)))

(defn log-stream-of
  "Fetches the lazy log stream from a running/dead container."
  [^String name]
  (u/unsafe! (docker/logs conn name)))
