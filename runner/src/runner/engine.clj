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

(ns runner.engine
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [contajners.core :as c]
            [failjure.core :as f]
            [taoensso.timbre :as log])
  (:import [java.io BufferedReader]))

(def conn
  {:uri (or (System/getenv "CONTAINER_ENGINE_URL")
            "http://localhost:8080")})

(def images
  (c/client {:engine   :podman
             :category :libpod/images
             :conn     conn
             :version  "v3.2.3"}))

(def containers
  (c/client {:engine   :podman
             :category :libpod/containers
             :conn     conn
             :version  "v3.2.3"}))

(def commit
  (c/client {:engine   :podman
             :category :libpod/commit
             :conn     conn
             :version  "v3.2.3"}))

(defn sh-tokenize
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
  "Pulls in an image.

  Returns the image or a failure"
  [image]
  (log/debugf "Pulling image %s" image)
  (f/try*
    (c/invoke images
              {:op               :ImagePullLibpod
               :params           {:reference image}
               :throw-exceptions true})
    image))

(defn delete-image
  "Idempotently deletes an image along with any untagged parent images
  that were referenced by that image by its full name or id."
  [image]
  (c/invoke images
            {:op     :ImageDeleteLibpod
             :params {:name image}})
  image)

(defn create-container
  "Creates a container from an image.

  Optionally takes the step and evars.
  Sets the working dir if the step needs a resource.

  Returns the id of the built container or a failure."
  ([image]
   (f/try-all [_ (log/debugf "Creating a container from %s" image)
               result (c/invoke containers
                                {:op               :ContainerCreateLibpod
                                 :data             {:image image}
                                 :throw-exceptions true})]
     (:Id result)
     (f/when-failed [err]
       (log/errorf "Could not create container: %s" (f/message err))
       err)))
  ([image step]
   (create-container image step {}))
  ([image {:keys [needs_resource cmd]} evars]
   (f/try-all [working-dir (some->> needs_resource
                                    (str "/root/"))
               command     (sh-tokenize cmd)
               _ (log/debugf "Creating new container with: image: %s cmd: %s evars: %s working-dir: %s"
                             image
                             command
                             evars
                             working-dir)
               result      (c/invoke
                             containers
                             {:op               :ContainerCreateLibpod
                              :data             {:image        image
                                                 :command      command
                                                 :env          evars
                                                 :work_dir     working-dir
                                                 :cgroups_mode "disabled"} ; unprivileged
                              :throw-exceptions true})]
     (:Id result)
     (f/when-failed [err]
       (log/errorf "Could not create container: %s" (f/message err))
       err))))

(defn delete-container
  "Idempotently and forcefully removes container by id."
  [id]
  (c/invoke containers
            {:op     :ContainerDeleteLibpod
             :params {:name  id
                      :force true}})
  id)

(defn inspect-container
  "Returns the container info by id."
  [id]
  (f/try-all [result (c/invoke containers
                               {:op               :ContainerInspectLibpod
                                :params           {:name id}
                                :throw-exceptions true})]
    result
    (f/when-failed [err]
      (log/errorf "Error fetching container info: %s" err)
      err)))

(defn status-of
  "Returns the status of a container"
  [id]
  (f/try-all [{{running? :Running exit-code :ExitCode} :State} (inspect-container id)]
    {:running?  running?
     :exit-code exit-code}
    (f/when-failed [err]
      (log/errorf "Could not fetch container status: %s" (f/message err))
      err)))

(defn react-to-log-line
  "Tails the stdout, stderr of a container
   and calls the reaction-fn with log lines
   as soon as they arrive."
  [id reaction-fn]
  (f/try-all [client     (c/client {:engine   :podman
                                    :category :containers
                                    :conn     conn
                                    :version  "v3.2.3"})
              log-stream (c/invoke client
                                   {:op               :ContainerLogs ; TODO: Use ContainerLogsLibpod
                                    :params           {:name   id
                                                       :follow (-> id
                                                                   status-of
                                                                   :running?)
                                                       :stdout true
                                                       :stderr true}
                                    :as               :stream
                                    :throw-exceptions true})]
    (future
      (with-open [rdr (io/reader log-stream)]
        (loop [r (BufferedReader. rdr)]
          (when-let [line (.readLine r)]
            (-> line
                (s/replace-first #"^\W+" "")
                (reaction-fn))
            (recur r)))))
    (f/when-failed [err]
      (log/errorf "Error following log line: %s" err)
      err)))

(defn start-container
  "Synchronously starts up a previously built container by id.

   Attaches to it and streams the logs.
   Takes container-id and run-id to tag the logs.

   Returns the id when complete or an error in case on non-zero exit."
  [id logging-fn]
  (f/try-all [_ (log/debugf "Starting container %s" id)
              _ (c/invoke containers
                          {:op               :ContainerStartLibpod
                           :params           {:name id}
                           :throw-exceptions true})
              _ (log/debugf "Attaching to container %s for logs" id)
              _ (react-to-log-line id logging-fn)
              status (c/invoke containers
                               {:op               :ContainerWaitLibpod ; TODO: Check why it blocks when container isn't running
                                :params           {:name id}
                                :throw-exceptions true})]
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
  (c/invoke containers
            {:op     :ContainerKillLibpod
             :params {:name id}})
  id)

(defn commit-container
  "Creates a new image from a container

  Returns image identifier or a failure."
  [container-id cmd]
  (f/try-all [result (c/invoke commit
                               {:op               :ImageCommitLibpod
                                :params           {:container container-id
                                                   :changes   (str "CMD=" cmd)}
                                :throw-exceptions true})]
    (:Id result)
    (f/when-failed [err]
      (log/errorf "Could not commit image: %s" (f/message err))
      err)))

(defn put-container-archive
  "Copies an tar input stream of either of the compressions:
   none, gzip, bzip2 or xz
   into the path of the container by id.

   Returns a Failure if failed."
  [id archive-input-stream path]
  (let [result (f/try*
                 (with-open [xin archive-input-stream]
                   (c/invoke containers
                             {:op               :PutContainerArchiveLibpod
                              :params           {:name id
                                                 :path path}
                              :data             xin
                              :throw-exceptions true})))]
    (when (f/failed? result)
      (log/errorf "Could not put archive in container: %s" result)
      result)))

(defn get-container-archive
  "Returns a tar stream of a path in the container by id."
  [id path]
  (f/try-all [result (c/invoke containers
                               {:op               :ContainerArchiveLibpod
                                :params           {:name id
                                                   :path path}
                                :as               :stream
                                :throw-exceptions true})]
    result
    (f/when-failed [err]
      (log/errorf "Error fetching container archive: %s" err)
      err)))

(defn container-ls
  "Returns the list of running containers"
  []
  (f/try*
    (c/invoke containers
              {:op :ContainerListLibpod})))

(comment
  (sh-tokenize "sh -c 'echo ${k1}'")

  (c/categories :podman "v3.2.3")

  (c/ops images)

  (c/doc images :ImagePullLibpod)

  (pull-image "busybox:musl")

  (delete-image "7550af44f")

  (c/ops containers)

  (c/doc containers :ContainerCreateLibpod)

  (create-container "busybox:musl")

  (pull-image "ubuntu")

  (create-container "busybox:musl"
                    {:cmd "sh -c 'for i in `seq 1 10`; do echo \"output: $i\"; sleep 1; done'"})

  (delete-container "f1d7045")

  (inspect-container "f00cbe2e06de")

  (status-of "f00cbe2e06de")

  (start-container "fb674e" println)

  (c/doc containers :ContainerLogs)

  (kill-container "07fb3da8")

  (commit-container "fb674e" "ls")

  (put-container-archive "8104b71ebe76"
                         (io/input-stream "test/test.tar")
                         "/roor")

  (get-container-archive "fb674e" "/root/test")

  (container-ls)

  (-> (pull-image "docker.io/library/busybox:musl")
      (create-container {:cmd "sh -c 'sleep 5; ls'"})
      (start-container println)))
