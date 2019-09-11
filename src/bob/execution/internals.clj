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
            [taoensso.timbre :as log]
            [bob.util :as u]
            [bob.states :as states]))

(defn has-image
  "Checks if an image is present locally.
  Returns the name or the error if any."
  [name]
  (let [result (f/try* (filter #(= (:RepoTags %) [name])
                               (docker/image-ls states/docker-conn)))]
    (if (or (f/failed? result) (zero? (count result)))
      (f/fail "Failed to find %s" name)
      name)))

(defn kill-container
  "Kills a running container using SIGKILL.
  Returns the name or the error if any."
  [name]
  (if (f/failed? (f/try* (docker/kill states/docker-conn name)))
    (do (log/errorf "Error killing container %s" name)
        (f/fail "Could not kill %s" name))
    name))

(defn pull
  "Pulls in an image if it's not present locally.
  Returns the name or the error if any."
  [name]
  (if (and (f/failed? (has-image name))
           (f/failed? (f/try* (do (log/debugf "Pulling image: %s" name)
                                  (docker/pull states/docker-conn name)
                                  (log/debugf "Pulled image: %s" name)))))
    (do (log/errorf "Could not pull image: %s" name)
        (f/fail "Cannot pull %s" name))
    name))

(defn build
  "Builds a container.
  Takes the base image and the entry point command.
  Returns the id of the built container."
  [image step evars]
  (let [resource    (:needs_resource step)
        working-dir (when resource (str "/root/" resource))
        cmd         (:cmd step)]
    (log/debugf "Creating new container with:
                 image: %s
                 entry point: %s
                 environment: %s
                 working dir: %s"
                image
                cmd
                evars
                working-dir)
    (f/try* (docker/create states/docker-conn
                           image
                           cmd
                           evars
                           {}
                           working-dir))))

(defn status-of
  "Returns the status of a container by id."
  [^String id]
  (let [result (f/try* (docker/container-state states/docker-conn id))]
    (if (f/failed? result)
      (do (log/errorf "Could not fetch container status: %s"
                      (f/message result))
          result)
      {:running?  (:Running result)
       :exit-code (:ExitCode result)})))

(defn run
  "Synchronously starts up a previously built container.
  Returns the id when complete or and error in case on non-zero exit."
  [id run-id]
  (f/try-all [_      (log/debugf "Starting container %s" id)
              _      (docker/start states/docker-conn id)
              _      (log/debugf "Attaching to container %s for logs" id)
              _      (docker/logs-live states/docker-conn
                                       id
                                       #(u/log-to-db % run-id))
              status (-> (docker/inspect states/docker-conn id)
                         :State
                         :ExitCode)]
    (if (zero? status)
      (u/format-id id)
      (do (log/debugf "Container %s exited with non-zero status: %s"
                     id
                     status)
          (f/fail "Abnormal exit.")))
    (f/when-failed [err]
      (log/errorf "Error in running container %s: %s"
                  id
                  (f/message err))
      err)))

(comment
  (build "busybox:musl"
         {:needs_resource "source"
          :cmd            "ls"}
         {})
  (log/infof "Creating new container with:
              image: %s
              entry point: %s
              env vars: %s
              working dir: %s"
             "busybox:musl"
             "echo Hello"
             {:k1 "v1"
              :k2 "v2"}
             "/root"))
