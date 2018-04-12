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
            [failjure.core :as f]
            [bob.util :refer [m]])
  (:import (com.spotify.docker.client DefaultDockerClient LogStream DockerClient$LogsParam)
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
  (let [result (perform! #(.searchImages docker name))]
    (if (or (f/failed? result) (zero? (count result)))
      (f/fail "%s not found" name)
      name)))

(defn- pull
  [name]
  (if (f/failed? (has-image name))
    (perform! #(.pull docker name))
    name))

(defn- build
  [^String image ^List cmd]
  (perform! #(let [config   (-> (ContainerConfig/builder)
                                (.hostConfig host-config)
                                (.image image)
                                (.cmd cmd)
                                (.build))
                   creation ^ContainerCreation (.createContainer docker config)]
               (.id creation))))

(defn- run
  [^String id]
  (let [result (perform! #(.startContainer docker id))]
    (if (f/failed? result)
      (format "Could not start: %s" (f/message result))
      (subs id 0 12))))

(defn start
  [_]
  (d/let-flow [result (f/ok-> (pull default-image)
                              (build default-command)
                              (run))]
              (m (if (f/failed? result)
                   (f/message result)
                   result))))

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
