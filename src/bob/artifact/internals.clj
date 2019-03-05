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

(ns bob.artifact.internals
  (:require [korma.core :as k]
            [clj-docker-client.core :as docker]
            [bob.execution.internals :as e]
            [bob.db.core :as db]
            [bob.util :as u])
  (:import (com.spotify.docker.client DefaultDockerClient)))

(defn get-stream-of
  "Fetches the TarStream of a path from a container."
  [path id]
  (u/unsafe! (docker/stream-path e/conn id path)))

(defn artifact-container-of
  "Returns the id of the container holding the artifact or nil."
  [pipeline number]
  (->> (k/select db/runs
                 (k/fields :last_pid)
                 (k/where {:pipeline pipeline
                           :number   number}))
       (first)
       :last_pid))

(defn get-artifact-path-of
  "Returns the registered path of an artifact of a pipeline or nil."
  [pipeline artifact-name]
  (->> (k/select db/artifacts
                 (k/fields :path)
                 (k/where {:pipeline pipeline
                           :name     artifact-name}))
       (first)
       :path))

(comment
  (->> (artifact-container-of "test:test" 1)
       (get-stream-of "/root/source"))
  (get-artifact-path-of "test:test" "source"))
