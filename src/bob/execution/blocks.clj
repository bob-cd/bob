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

(ns bob.execution.blocks
  (:require [clojure.string :refer [split-lines]]
            [failjure.core :as f]
            [bob.util :refer [m]])
  (:import (com.spotify.docker.client DefaultDockerClient DockerClient$LogsParam DockerClient$ListImagesParam LogStream)
           (com.spotify.docker.client.messages HostConfig ContainerConfig ContainerCreation)
           (java.util List)))

(def default-image "debian:latest")

(def default-command ["bash" "-c" "while sleep 1; do echo ${RANDOM}; done"])

(def docker ^DefaultDockerClient (.build (DefaultDockerClient/fromEnv)))

(def host-config ^HostConfig (.build (HostConfig/builder)))

(def log-params (into-array DockerClient$LogsParam
                            [(DockerClient$LogsParam/stdout)
                             (DockerClient$LogsParam/stderr)]))

(defn- perform!
  [action]
  (f/try* (action)))

(defn- has-image
  [name]
  (let [result (perform! #(.listImages docker (into-array DockerClient$ListImagesParam
                                                          [(DockerClient$ListImagesParam/byName name)])))]
    (if (or (f/failed? result) (zero? (count result)))
      (f/fail "Failed to find %s" name)
      name)))

(defn pull
  [name]
  (if (and (f/failed? (has-image name)) (f/failed? (perform! #(do (println (format "Pulling %s" name))
                                                                  (.pull docker name)
                                                                  (println (format "Pulled %s" name))))))
    (f/fail "Cannot pull %s" name)
    name))

(defn build
  [^String image ^List cmd]
  (perform! #(let [config   (-> (ContainerConfig/builder)
                                (.hostConfig host-config)
                                (.image image)
                                (.cmd cmd)
                                (.build))
                   creation ^ContainerCreation (.createContainer docker config)]
               (.id creation))))

(defn run
  [^String id]
  (let [result (perform! #(.startContainer docker id))]
    (if (f/failed? result)
      (format "Run failed due to %s" (f/message result))
      (subs id 0 12))))

(defn log-stream-of
  [name]
  (perform! #(.logs docker name log-params)))

(defn read-log-stream
  [^LogStream stream count]
  (perform! #(->> stream
                  (.readFully)
                  (split-lines)
                  (take count))))

(defn kill-container
  [name]
  (if (f/failed? (perform! #(.killContainer docker name)))
    (f/fail "Could not kill %s" name)
    name))

(defn remove-container
  [name]
  (perform! #(.removeContainer docker name)))
