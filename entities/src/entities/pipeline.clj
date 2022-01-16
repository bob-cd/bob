; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.pipeline
  (:require [xtdb.api :as xt]
            [entities.db :as db]))

(defn create
  "Creates a pipeline using the supplied data."
  [db-client queue-chan data]
  (let [id   (keyword (format "bob.pipeline.%s/%s"
                              (:group data)
                              (:name data)))
        data (-> data
                 (assoc :xt/id id)
                 (assoc :type :pipeline))]
    (db/validate-and-transact db-client
                              queue-chan
                              :bob.command.pipeline-create/data
                              data
                              [[::xt/put data]]
                              "pipeline")))

(defn delete
  "Deletes a pipeline."
  [db-client _queue-chan data]
  (let [id (keyword (format "bob.pipeline.%s/%s"
                            (:group data)
                            (:name data)))]
    (db/validate-and-transact db-client
                              nil
                              :bob.command.pipeline-delete/data
                              data
                              [[::xt/delete id]]
                              "pipeline")))
