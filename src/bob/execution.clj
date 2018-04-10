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

(ns bob.execution
  (:require [clojure.string :refer [split-lines]]
            [manifold.deferred :as d]
            [bob.util :refer [m]])
  (:import (com.spotify.docker.client DefaultDockerClient LogStream DockerClient$LogsParam)
           (com.spotify.docker.client.messages HostConfig ContainerConfig ContainerCreation)))

(def default-image "debian:latest")

(def docker ^DefaultDockerClient (.build (DefaultDockerClient/fromEnv)))

(def host-config ^HostConfig (.build (HostConfig/builder)))

(def log-params (into-array DockerClient$LogsParam
                            [(DockerClient$LogsParam/stdout)
                             (DockerClient$LogsParam/stderr)]))

(defn- run
  []
  (let [config   (-> (ContainerConfig/builder)
                     (.hostConfig host-config)
                     (.image default-image)
                     (.cmd ["bash" "-c" "while sleep 1; do echo ${RANDOM}; done"])
                     (.build))
        creation ^ContainerCreation (.createContainer docker config)
        id       (.id creation)]
    (do (.startContainer docker id)
        id)))

(defn start
  [_]
  (d/let-flow [id ^String (run)]
              (m (subs id 0 12))))

(defn logs-of
  [id count]
  (d/let-flow [log-stream ^LogStream (.logs docker id log-params)
               log (->> log-stream
                        (.readFully)
                        (split-lines)
                        (take count))]
              (m log)))

(defn stop
  [id]
  (d/let-flow [_ (.killContainer docker id)
               _ (.removeContainer docker id)]
              (m true)))
