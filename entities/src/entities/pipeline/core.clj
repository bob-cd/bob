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

(ns entities.pipeline.core
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [crux.api :as crux]
            [entities.errors :as err]))

(defn create
  "Creates a new pipeline.

  Takes a map of the following:
  - group
  - name
  - a list of steps
  - a map of environment vars
  - a list of resources
  - a starting Docker image.

  The group defines a logical grouping of pipelines like dev or staging
  and the name is the name of the pipeline like build or test.

  Returns Ok or the error if any."
  [db-client queue-chan pipeline]
  (let [id     (keyword (format "bob.pipeline.%s/%s"
                                (:group pipeline)
                                (:name pipeline)))
        data   (-> pipeline
                   (dissoc :group :name)
                   (assoc :crux.db/id id)
                   (assoc :type :pipeline))
        result (f/try*
                 (crux/submit-tx db-client [[:crux.tx/put data]]))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Pipeline creation failed: %s" (f/message result)))
      "Ok")))

(defn delete
  "Deletes a pipeline"
  [db-client _queue-chan pipeline]
  (let [id (keyword (format "bob.pipeline.%s/%s"
                            (:group pipeline)
                            (:name pipeline)))]
    (log/debugf "Deleting pipeline %s" pipeline)
    (f/try*
      (crux/submit-tx db-client [[:crux.tx/delete id]]))
    "Ok"))

(comment
  (require '[entities.system :as sys]
           '[com.stuartsierra.component :as c])

  (def db
    (c/start (sys/->Database "bob" "localhost" 5432 "bob" "bob")))

  (c/stop db)

  (create (sys/db-client db)
          nil
          {:group     "test"
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
           :image     "busybox:musl"})

  (crux/entity (-> db
                   sys/db-client
                   crux/db)
               :bob.pipeline.test/test)

  (delete (sys/db-client db)
          nil
          {:group "test"
           :name  "test"}))
