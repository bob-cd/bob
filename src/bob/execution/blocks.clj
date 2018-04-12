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
  (:import (com.spotify.docker.client DefaultDockerClient DockerClient$LogsParam)
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

(defn has-image
  [name]
  (let [result (perform! #(.searchImages docker name))]
    (if (or (f/failed? result) (zero? (count result)))
      (f/fail "%s not found" name)
      name)))

(defn pull
  [name]
  (if (f/failed? (has-image name))
    (perform! #(.pull docker name))
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
      (format "Could not start: %s" (f/message result))
      (subs id 0 12))))
