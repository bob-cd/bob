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

(ns runner.docker
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clj-docker-client.core :as docker]
            [failjure.core :as f]
            [taoensso.timbre :as log]))

(defonce conn
         {:uri         "unix:///var/run/docker.sock"
          :api-version "v1.40"})

(defonce images
         (docker/client {:category :images
                         :conn     conn}))

(defonce containers
         (docker/client {:category :containers
                         :conn     conn}))

(defonce commit
         (docker/client {:category :commit
                         :conn     conn}))

(defn sh-tokenize!
  "Tokenizes a shell command given as a string into the command and its args.

  Either returns a list of tokens or throws an IllegalStateException.

  Sample input: sh -c 'while sleep 1; do echo \\\"${RANDOM}\\\"; done'
  Output: [sh, -c, while sleep 1; do echo \"${RANDOM}\"; done]"
  [command]
  (let [[escaped? current-arg args state]
        (loop [cmd         command
               escaped?    false
               state       :no-token
               current-arg ""
               args        []]
          (if (or (nil? cmd) (zero? (count cmd)))
            [escaped? current-arg args state]
            (let [char ^Character (first cmd)]
              (if escaped?
                (recur (rest cmd) false state (str current-arg char) args)
                (case state
                  :single-quote       (if (= char \')
                                        (recur (rest cmd) escaped? :normal current-arg args)
                                        (recur (rest cmd) escaped? state (str current-arg char) args))
                  :double-quote       (case char
                                        \" (recur (rest cmd) escaped? :normal current-arg args)
                                        \\ (let [next (second cmd)]
                                             (if (or (= next \") (= next \\))
                                               (recur (drop 2 cmd) escaped? state (str current-arg next) args)
                                               (recur (drop 2 cmd) escaped? state (str current-arg char next) args)))
                                        (recur (rest cmd) escaped? state (str current-arg char) args))
                  (:no-token :normal) (case char
                                        \\ (recur (rest cmd) true :normal current-arg args)
                                        \' (recur (rest cmd) escaped? :single-quote current-arg args)
                                        \" (recur (rest cmd) escaped? :double-quote current-arg args)
                                        (if-not (Character/isWhitespace char)
                                          (recur (rest cmd) escaped? :normal (str current-arg char) args)
                                          (if (= state :normal)
                                            (recur (rest cmd) escaped? :no-token "" (conj args current-arg))
                                            (recur (rest cmd) escaped? state current-arg args))))
                  (throw (IllegalStateException. (format "Invalid shell command: %s, unexpected token %s found."
                                                         command
                                                         state))))))))]
    (if escaped?
      (conj args (str current-arg \\))
      (if (not= state :no-token)
        (conj args current-arg)
        args))))

(defn pull-image
  "Pulls in an image if it's not present locally.

  Returns the image or a failure"
  [image]
  (log/debugf "Pulling image %s" image)
  (f/try*
    (docker/invoke images
                   {:op               :ImageCreate
                    :params           {:fromImage image}
                    :throw-exception? true})
    image))

(defn commit-image
  "Creates a new image from a container
   with the name: `container-id/timestamp:latest`

  Returns image identifier"
  [container-id cmd]
  (f/try-all [result (docker/invoke commit
                                    {:op               :ImageCommit
                                     :params           {:container       container-id
                                                        :repo            (format "%s/%d"
                                                                                 container-id
                                                                                 (System/currentTimeMillis))
                                                        :tag             "latest"
                                                        :containerConfig {:Cmd [cmd]}}
                                     :throw-exception? true})]
    (:Id result)
    (f/when-failed [err]
      (log/errorf "Could not commit image: %s" (f/message err))
      err)))

(defn delete-image
  "Idempotently deletes an image along with any untagged parent images
  that were referenced by that image by its full name or id."
  [image]
  (docker/invoke images
                 {:op     :ImageDelete
                  :params {:name image}})
  image)

(defonce host-config
         {:Mounts [{:Target "/var/run/docker.sock"
                    :Source "/var/run/docker.sock"
                    :Type   "bind"}]})

(defn create-container
  "Creates a container from an image.

  Optionally takes the step and evars.
  Sets the working dir if the step needs a resource.

  Returns the id of the built container."
  ([image]
   (f/try-all [_      (log/debugf "Creating a container from %s" image)
               result (docker/invoke containers
                                     {:op               :ContainerCreate
                                      :params           {:body {:Image      image
                                                                :HostConfig host-config}}
                                      :throw-exception? true})]
     (:Id result)
     (f/when-failed [err]
       (log/errorf "Could not create container: %s" (f/message err))
       err)))
  ([image step] (create-container image step {}))
  ([image {:keys [needs_resource cmd]} evars]
   (f/try-all [working-dir     (some->> needs_resource
                                        (str "/root/"))
               command         (sh-tokenize! cmd)
               formatted-evars (mapv #(format "%s=%s"
                                              (name (key %))
                                              (val %))
                                     evars)
               _               (log/debugf "Creating new container with: image: %s cmd: %s evars: %s working-dir: %s"
                                           image
                                           command
                                           formatted-evars
                                           working-dir)
               result          (docker/invoke
                                 containers
                                 {:op               :ContainerCreate
                                  :params           {:body {:Image      image
                                                            :Cmd        command
                                                            :Env        formatted-evars
                                                            :WorkingDir working-dir
                                                            :HostConfig host-config}}
                                  :throw-exception? true})]
     (:Id result)
     (f/when-failed [err]
       (log/errorf "Could not create container: %s" (f/message err))
       err))))

(defn inspect-container
  "Returns the container info by id."
  [id]
  (f/try-all [result (docker/invoke containers
                                    {:op               :ContainerInspect
                                     :params           {:id id}
                                     :throw-exception? true})]
    result
    (f/when-failed [err]
      (log/errorf "Error fetching container info: %s" err)
      err)))

(defn status-of
  "Returns the status of a container"
  [id]
  (f/try-all [{:keys [State]} (inspect-container id)]
    {:running?  (:Running State)
     :exit-code (:ExitCode State)}
    (f/when-failed [err]
      (log/errorf "Could not fetch container status: %s" (f/message err))
      err)))

(defn react-to-log-line
  "Tails the stdout, stderr of a container
   and calls the reaction-fn with log lines
   as soon as they arrive."
  [id reaction-fn]
  (f/try-all [log-stream (docker/invoke containers
                                        {:op               :ContainerLogs
                                         :params           {:id     id
                                                            :follow true
                                                            :stdout true
                                                            :stderr true}
                                         :as               :stream
                                         :throw-exception? true})]
    (future (with-open [rdr (io/reader log-stream)]
              (loop [r (java.io.BufferedReader. rdr)]
                (when-let [line (.readLine r)]
                  (let [log-string (s/replace-first line #"^\W+" "")]
                    (when (not (s/blank? log-string))
                      (reaction-fn log-string))
                    (recur r))))))
    (f/when-failed [err]
      (log/errorf "Error following log line: %s" err)
      err)))

(defn start-container
  "Synchronously starts up a previously built container by id.

   Attaches to it and streams the logs.
   Takes container-id and run-id to tag the logs.

   Returns the id when complete or an error in case on non-zero exit."
  [id logging-fn]
  (f/try-all [_      (log/debugf "Starting container %s" id)
              _      (docker/invoke containers
                                    {:op               :ContainerStart
                                     :params           {:id id}
                                     :throw-exception? true})
              _      (log/debugf "Attaching to container %s for logs" id)
              _      (react-to-log-line id logging-fn)
              status (:StatusCode (docker/invoke containers
                                                 {:op               :ContainerWait
                                                  :params           {:id id}
                                                  :throw-exception? true}))]
    (if (zero? status)
      id
      (let [msg (format "Container %s exited with non-zero status %d"
                        id
                        status)]
        (log/debug msg)
        (f/fail msg)))
    (f/when-failed [err]
      (log/errorf "Error running container: %s" (f/message err))
      err)))

(defn kill-container
  "Idempotently kills a running container using SIGKILL by id."
  [id]
  (docker/invoke containers
                 {:op     :ContainerKill
                  :params {:id id}})
  id)

(defn delete-container
  "Idempotently and forcefully removes container by id."
  [id]
  (docker/invoke containers
                 {:op     :ContainerDelete
                  :params {:id    id
                           :force true}})
  id)

(defn put-container-archive
  "Copies an tar input stream of either of the compressions:
   none, gzip, bzip2 or xz
   into the path of the container by id.

   Returns a Failure if failed."
  [id archive-input-stream path]
  (let [result (f/try*
                 (with-open [xin archive-input-stream]
                   (docker/invoke containers
                                  {:op               :PutContainerArchive
                                   :params           {:id          id
                                                      :path        path
                                                      :inputStream xin}
                                   :throw-exception? true})))]
    (when (f/failed? result)
      (log/errorf "Could not put archive in container: %s" result)
      result)))

(defn get-container-archive
  "Returns a tar stream of a path in the container by id."
  [id path]
  (f/try-all [result (docker/invoke containers
                                    {:op               :ContainerArchive
                                     :params           {:id   id
                                                        :path path}
                                     :as               :stream
                                     :throw-exception? true})]
    result
    (f/when-failed [err]
      (log/errorf "Error fetching container archive: %s" err)
      err)))

(defn container-ls
  "Returns the list of running containers"
  []
  (f/try*
    (docker/invoke containers
                   {:op :ContainerList})))

(defn pause-container
  "Idempotently pause a container"
  [id]
  (docker/invoke containers
                 {:op     :ContainerPause
                  :params {:id id}}))

(defn unpause-container
  "Idempotently unpause a container"
  [id]
  (docker/invoke containers
                 {:op     :ContainerUnpause
                  :params {:id id}}))

(comment
  (pull-image "alpine:latest")

  (delete-image "alpine:latest")

  (sh-tokenize! "sh -c 'echo ${k1}'")

  (create-container "busybox:musl"
                    {:needs_resource "src"
                     :cmd            "sh -c 'i=1; while :; do echo $i; sleep 1; i=$((i+1)); done'"}
                    {:k1 "v1"})

  (create-container "busybox:musl" {:cmd "sh -c 'sleep 1; exit 1'"})

  (create-container "docker:stable" {:cmd "docker info"})

  (inspect-container "conny")

  (status-of "conny")

  (start-container "f99" "yes")

  (kill-container "conny")

  (delete-container "conny")

  (put-container-archive "conny" (io/input-stream "src.tar") "/root")

  (get-container-archive "conny" "/root/files")

  (container-ls)

  (pause-container "yes")

  (-> "yes"
      inspect-container
      :State
      :Paused)

  (unpause-container "yes"))
