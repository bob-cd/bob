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
;   You should have received a copy of the GNU Affero Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.execution.internals
  (:require [clojure.string :as cs]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [taoensso.timbre :as log]
            [bob.util :as u]
            [bob.states :as states]))

(defn has-image
  "Checks if an image is present locally.
  Returns the name or the error if any."
  [name]
  (let [result (filter #(= (:RepoTags %) [name])
                       (docker/invoke states/images {:op :ImageList}))]
    (if (empty? result)
      (u/log-and-fail "Failed to find" name "locally")
      (do (log/debugf "Found image locally: %s" name)
          name))))

(defn pull-image
  "Pulls in an image if it's not present locally.
  Returns the name or the error if any."
  [name]
  (letfn [(validate-name [name]
            (let [split-name (cs/split name #":")]
              (if (= 2 (count split-name))
                {:repo (first split-name)
                 :tag  (second split-name)}
                (u/log-and-fail "Please provide a repository and a tag as image name:" split-name))))
          (pull-invoke [split-name]
            (let [{:keys [repo tag]} split-name
                  _      (log/debugf "Pulling image with repo %s and tag %s" repo tag)
                  result (docker/invoke states/images {:op :ImageCreate
                                                       :params {:fromImage repo
                                                                :tag tag}})]
              (if (contains? result :message)
                (f/fail "Could not pull image %s:%s" repo tag)
                result)))]
    (if (and (f/failed? (has-image name))
             (f/failed? (pull-invoke (validate-name name))))
      (u/log-and-fail "Could not pull image:" name)
      (do (log/debugf "Successfully pulled image: %s" name)
          name))))

(defn commit-image
  "Create a new image from a container
   Defaults to repo containing of container-id a timestamp
   and tag latest

   Returns image identifier"
  [container-id cmd]
  (let [repo   (format "%s/%d" container-id (System/currentTimeMillis))
        result (docker/invoke states/commit {:op     :ImageCommit
                                             :params {:container       container-id
                                                      :repo            repo
                                                      :tag             "latest"
                                                      :containerConfig {:Cmd [cmd]}}})]
    (if (contains? result :message)
      (u/log-and-fail "Could not commit image:" (:message result))
      (:Id result))))

(defn delete-image
  "Remove an image, along with any untagged parent images that were referenced by that image.
   Takes image id or repo-tag-combination, latest is appended if no tag is provided.

   Returns id of deleted image."
  [image]
  (let [_      (log/debugf "Deleting image with id %s" image)
        result (docker/invoke states/images {:op :ImageDelete
                                             :params {:name image}})]
    (if (contains? result :message)
      (u/log-and-fail "Could not delete image:" (:message result))
      result)))

(defn create-container
  "Creates a container.
  Takes the base image and optionally the step and evars.
  Returns the id of the built container."
  ([image] (let [_      (log/debugf "Creating new container with:
                                     image: %s" image)
                 result (docker/invoke states/containers {:op :ContainerCreate
                                                          :params {:body {:Image image}}})]
             (if (contains? result :message)
               (u/log-and-fail "Could not build container with image:" (:message result))
               (:Id result))))
  ([image step] (create-container image step {}))
  ([image step evars] (let [resource        (:needs_resource step)
                            working-dir     (when resource (str "/root/" resource))
                            cmd             (u/sh-tokenize! (:cmd step))
                            formatted-evars (u/format-env-vars evars)
                            _               (log/debugf "Creating new container with:
                                                        image: %s
                                                        cmd: %s
                                                        evars: %s
                                                        working-dir: %s"
                                                        image
                                                        cmd
                                                        (vec formatted-evars)
                                                        working-dir)
                            result          (docker/invoke states/containers {:op :ContainerCreate
                                                                              :params {:body {:Image      image
                                                                                              :Cmd        cmd
                                                                                              :Env        formatted-evars
                                                                                              :WorkingDir working-dir}}})]
                        (if (contains? result :message)
                          (u/log-and-fail "Could not build container with image:" (:message result))
                          (:Id result)))))

(defn status-of
  "Returns the status of a container by id."
  [^String id]
  (let [result (docker/invoke states/containers {:op :ContainerInspect
                                                 :params {:id id}})
        running? (-> result :State :Running)
        exit-code (-> result :State :ExitCode)]
    (if (contains? result :message)
      (u/log-and-fail (format "Could not fetch container status: %s" (:message result)))
      {:running? running? :exit-code exit-code})))

(defn logs-live
  "Get stdout and stderr logs from a container."
  [id reaction-fn]
  (let [log-stream (docker/invoke states/containers {:op :ContainerLogs
                                                     :params
                                                     {:id id
                                                      :follow true
                                                      :stdout true}
                                                     :as :stream})]
    (future
      (with-open [rdr (clojure.java.io/reader log-stream)]
        (loop [r (java.io.BufferedReader. rdr)]
          (when-let [line (.readLine r)]
            (let [log-string (clojure.string/replace-first line #"^\W+" "")]
              (when (not (cs/blank? log-string)) (reaction-fn log-string))
              (recur r))))))))

(defn start-container
  "Synchronously starts up a previously built container by id.
   Takes container-id and run-id which is the id of the build.

   Returns the id when complete or an error in case on non-zero exit."
  [id run-id]
  (f/try-all [_      (log/debugf "Starting container %s" id)
              _      (docker/invoke states/containers {:op :ContainerStart :params {:id id}})
              _      (log/debugf "Attaching to container %s for logs" id)
              _      (logs-live id #(u/log-to-db % run-id))
              status (:StatusCode (docker/invoke states/containers {:op :ContainerWait :params {:id id}}))]
             (do (f/when-failed [err]
                                (u/log-and-fail "Error in running container"
                                                (str id ":")
                                                (f/message err)))
                 (if (zero? status)
                   (u/format-id id)
                   (u/log-and-fail "Container with id"
                                   id
                                   "exited with non-zero status:"
                                   status)))))

(defn kill-container
  "Kills a running container using SIGKILL.
  Returns the name or the error if any."
  [name]
  (let [result (docker/invoke states/containers {:op :ContainerKill :params {:id name}})]
    (if (cs/blank? result)
      name
      (u/log-and-fail "Could not kill" name))))

(defn delete-container
  "Removes container by id. Takes optional keyword argument :force to force-remove container.
   Defaults to force-remove.

  Returns a Failure object if failed."
  ([id]
   (delete-container id :force))
  ([id force-flag]
   (let [id         (str id)
         force-flag (= force-flag :force)
         _          (log/debugf "Deleting container with id %s" id)
         result     (docker/invoke states/containers
                                   {:op     :ContainerDelete
                                    :params {:id    id
                                             :force force-flag}})]
     (if-let [message (get result :message)]
       (u/log-and-fail "Could not delete container:" message)
       result))))

(comment
  (docker/ops states/images)
  (docker/ops states/containers)
  (docker/doc states/images :ImageCreate)
  (docker/invoke states/images {:op :ImageCreate :params {:fromImage "clojure" :tag "latest"}})
  (docker/invoke states/images {:op :ImageDelete :params {:name "clojure:latest"}})
  (def myid (:Id (docker/invoke states/containers {:op :ContainerCreate
                                                   :params {:body {:Image      "oracle/graalvm-ce:19.3.0"
                                                                   :Cmd        "sh -c 'touch test.txt && echo $PATH >> test.txt'"
                                                                   :Env        ""
                                                                   :WorkingDir nil}}})))
  (docker/invoke states/containers {:op :ContainerStart :params {:id myid}})
  (:StatusCode (docker/invoke states/containers {:op :ContainerWait :params {:id myid}}))

  (create-container "bobcd/bob:latest"
                    {:needs_resource "source"
                     :cmd            "ls"})

  (defn validate-name [name]
    (let [split-name (cs/split name #":")]
      (if (= 2 (count split-name))
        {:repo (first split-name)
         :tag  (second split-name)}
        (u/log-and-fail "Please provide a repository and a tag as image name:" split-name))))

  (defn pull-invoke [split-name]
    (let [{:keys [repo tag]} split-name
          _      (log/debugf "Pulling image with repo %s and tag %s" repo tag)
          result (docker/invoke states/images {:op :ImageCreate
                                               :params {:fromImage repo
                                                        :tag tag}})]
      (if (contains? result :message)
        (f/fail "Could not pull image %s:%s" repo tag)
        result)))

  (def name "bobcd/bob:latest")
  (has-image name)
  (validate-name name)
  (pull-invoke (validate-name name))
  (pull-invoke {:repo "oracle/graalvm-ce" :tags "19.3.0"})
  (pull-image "docker.io/bobcd/bob:latest")
  (if (and (f/failed? (has-image name))
           (f/failed? (pull-invoke (validate-name name))))
    (u/log-and-fail "Could not pull image:" name)
    (do (log/debugf "Successfully pulled image: %s" name)
        name))

  (docker/invoke states/images {:op :ImageCreate
                                :params {:fromImage "alpine"
                                         :tag "3.11.3"}})
  (:ExitCode (:State (docker/invoke states/containers {:op :ContainerInspect :params {:id "82edce1bfdea"}})))

  (docker/invoke states/containers {:op :ContainerStart :params {:id "interesting_swirles"}})
  (:StatusCode (docker/invoke states/containers {:op :ContainerWait :params {:id "interesting_swirles"}}))

  (docker/invoke states/containers {:op :ContainerDelete :params {:id "16a4b4e860b4" :force true}})
  (delete-container "3c12417d5a7e" true)

  (docker/invoke states/images {:op :ImageDelete
                                :params {:name "clojure:latest"}})

  (delete-image "clojure")
  (clojure.string/replace-first "bin usr" #"^\W+" ""))
