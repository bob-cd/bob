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
            [clj-docker-client.core :as docker]
            [bob.util :as u]
            [bob.states :as states]))

(defn- has-image
  "Checks if an image is present locally.
  Returns the name or the error if any."
  [name]
  (let [result (u/unsafe! (filter #(= (-> % :RepoTags) [name])
                                  (docker/image-ls states/docker-conn)))]
    (if (or (f/failed? result) (zero? (count result)))
      (f/fail "Failed to find %s" name)
      name)))

(defn kill-container
  "Kills a running container using SIGKILL.
  Returns the name or the error if any."
  [name]
  (if (f/failed? (u/unsafe! (docker/kill states/docker-conn name)))
    (f/fail "Could not kill %s" name)
    name))

(defn pull
  "Pulls in an image if it's not present locally.
  Returns the name or the error if any."
  [name]
  (if (and (f/failed? (has-image name))
           (f/failed? (u/unsafe! (do (println (format "Pulling %s" name))
                                     (docker/pull states/docker-conn name)
                                     (println (format "Pulled %s" name))))))
    (f/fail "Cannot pull %s" name)
    name))

(defn build
  "Builds a container.
  Takes the base image and the entry point command.
  Returns the id of the built container."
  [image step evars]
  (let [resource    (:needs_resource step)
        working-dir (when resource (str "/root/" resource))]
    (u/unsafe! (docker/create states/docker-conn
                              image
                              (:cmd step)
                              evars
                              {}
                              working-dir))))

(defn status-of
  "Returns the status of a container by id."
  [^String id]
  (let [result (u/unsafe! (docker/container-state states/docker-conn id))]
    (if (f/failed? result)
      (f/message result)
      {:running?  (:Running result)
       :exit-code (:ExitCode result)})))

(defn run
  "Synchronously starts up a previously built container.
  Returns the id when complete or and error in case on non-zero exit."
  [^String id]
  (f/attempt-all [_      (u/unsafe! (docker/start states/docker-conn id))
                  status (u/unsafe! (docker/wait-container states/docker-conn id))]
    (if (zero? status)
      (u/format-id id)
      (f/fail "Abnormal exit."))
    (f/when-failed [err] err)))

(defn log-stream-of
  "Fetches the lazy log stream from a running/dead container."
  [^String name]
  (u/unsafe! (docker/logs states/docker-conn name)))

(comment
  (build "busybox:musl"
         {:needs_resource "source"
          :cmd            "ls"}
         {}))
