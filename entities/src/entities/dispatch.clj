;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns entities.dispatch
  (:require [taoensso.timbre :as log]
            [entities.pipeline.core :as pipeline]))

(def ^:private routes
  {:pipeline/create pipeline/create
   :pipeline/delete pipeline/delete})

(defn route
  [message]
  (log/debugf "Routing message: %s" message)
  (let [msg-type  (keyword (:type message))
        routed-fn (msg-type routes)]
    (routed-fn (:payload message))))

(comment
  (route {:type    "pipeline/create"
          :payload {:group     "test"
                    :name      "test"
                    :steps     [{:cmd "echo hello"}
                                {:needs_resource "source"
                                 :cmd            "ls"}]
                    :vars      {:k1 "v1"
                                :k2 "v2"}
                    :resources [{:name     "source"
                                 :type     "external"
                                 :provider "git"
                                 :params   {:repo   "https://github.com/bob-cd/bob"
                                            :branch "master"}}
                                {:name     "source2"
                                 :type     "external"
                                 :provider "git"
                                 :params   {:repo   "https://github.com/lispyclouds/clj-docker-client"
                                            :branch "master"}}]
                    :image     "busybox:musl"}})
  (route {:type    "pipeline/delete"
          :payload {:name  "test"
                    :group "test"}}))
