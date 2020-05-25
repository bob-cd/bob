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
            [next.jdbc :as jdbc]
            [entities.pipeline.db :as db]
            [entities.errors :as err]))

(defn- name-of
  [p-group p-name]
  (format "%s:%s"
          p-group
          p-name))

(defn- insert-pipeline
  [txn image pipeline]
  (log/debugf "Inserting pipeline %s" pipeline)
  (db/insert-pipeline txn
                      {:name  pipeline
                       :image image})
  txn)

(defn- insert-resources
  [txn resources pipeline]
  (doseq [resource resources]
    (let [{:keys [params provider]} resource
          rname                     (:name resource)
          rtype                     (:type resource)]
      (log/debugf "Inserting resource: name: %s type: %s provider: %s"
                  rname
                  rtype
                  provider)
      (db/insert-resource txn
                          {:name     rname
                           :type     rtype
                           :pipeline pipeline
                           :provider provider})
      (when (seq params)
        (log/debugf "Adding params %s for resource %s"
                    params
                    rname)
        (db/insert-resource-params txn
                                   {:params (map #(vector (name (first %))
                                                          (last %)
                                                          rname
                                                          pipeline)
                                                 params)}))))
  txn)

(defn- insert-evars
  [txn evars pipeline]
  (when (seq evars)
    (let [evar-values (map #(vector (name (first %))
                                    (last %)
                                    pipeline)
                           evars)]
      (db/insert-evars txn {:evars evar-values})))
  txn)

(defn- insert-steps
  [txn steps pipeline]
  (doseq [step steps]
    (let [{cmd :cmd
           needs-resource :needs_resource
           {artifact-name  :name
            artifact-path  :path
            artifact-store :store}
           :produces_artifact}
          step]
      (log/debugf "Inserting step: cmd: %s resource: %s artifact: %s"
                  cmd
                  needs-resource
                  (:produces_artifact step))
      (db/insert-step txn
                      {:cmd               cmd
                       :needs_resource    needs-resource
                       :produces_artifact artifact-name
                       :artifact_path     artifact-path
                       :artifact_store    artifact-store
                       :pipeline          pipeline})))
  txn)

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
  [db-conn
   queue-chan
   {:keys [group steps vars resources image]
    :as   p}]
  (let [pipeline (name-of group (:name p))
        result   (f/try* (jdbc/with-transaction [txn db-conn]
                           (-> txn
                               (insert-pipeline image pipeline)
                               (insert-resources resources pipeline)
                               (insert-evars vars pipeline)
                               (insert-steps steps pipeline))))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Pipeline creation failed: %s" (f/message result)))
      "Ok")))

(defn delete
  "Deletes a pipeline"
  [db-conn _queue-chan pipeline]
  (let [pipeline (name-of (:group pipeline) (:name pipeline))]
    (log/debugf "Deleting pipeline %s" pipeline)
    (f/try* (db/delete-pipeline db-conn {:name pipeline}))
    "Ok"))

(comment
  (require '[entities.system :as sys])
  (let [conn (-> sys/system
                 :database
                 sys/db-connection)]
    (create conn
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
    (delete conn
            nil
            {:group "test"
             :name  "test"})))
