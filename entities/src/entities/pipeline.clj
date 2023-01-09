; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.pipeline
  (:require
    [entities.db :as db]
    [failjure.core :as f]
    [taoensso.timbre :as log]
    [xtdb.api :as xt]))

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

(defn- logs-of
  [db-client run-id]
  (->> (xt/q (xt/db db-client)
             {:find  '[(pull log [:xt/id])]
              :where [['log :type :log-line]
                      ['log :run-id run-id]]})
       (map first)
       (map :xt/id)))

(defn- runs-of
  [db-client group name]
  (->> (xt/q (xt/db db-client)
             {:find  '[(pull run [:xt/id])]
              :where ['[run :type :pipeline-run]
                      ['run :group group]
                      ['run :name name]]})
       (map first)
       (map :xt/id)))

(defn delete
  "Deletes a pipeline along with its associated resources."
  [db-client _queue-chan {:keys [group name] :as data}]
  (log/infof "Deleting pipeline, runs and logs for (%s, %s)"
             group
             name)
  (f/try-all [id   (keyword (format "bob.pipeline.%s/%s"
                                    group
                                    name))
              runs (runs-of db-client group name)
              logs (mapcat #(logs-of db-client %) runs)
              txn  (map #(vector ::xt/delete
                                 %)
                        (concat logs runs [id]))]
    (db/validate-and-transact db-client
                              nil
                              :bob.command.pipeline-delete/data
                              data
                              txn
                              "pipeline")
    (f/when-failed [err]
      (log/errorf "Pipeline deletion error: %s" (f/message err)))))
