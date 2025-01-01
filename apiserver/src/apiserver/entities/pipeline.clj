; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.pipeline
  (:require
   [clojure.tools.logging :as log]
   [common.events :as ev]
   [failjure.core :as f]
   [xtdb.api :as xt]))

(defn create
  "Creates a pipeline using the supplied data."
  [db-client {:keys [producer]} {:keys [group name] :as data}]
  (let [id (keyword (format "bob.pipeline.%s/%s" group name))]
    (log/infof "Creating pipeline %s with id %s" data id)
    (xt/await-tx
     db-client
     (xt/submit-tx db-client [[::xt/put (assoc data :xt/id id :type :pipeline)]]))
    (ev/emit producer
             {:type "Normal"
              :kind "Pipeline"
              :reason "PipelineCreate"
              :message (format "Pipeline created/updated %s/%s" group name)})))

(defn- logs-of
  [db-client run-id]
  (->> (xt/q (xt/db db-client)
             {:find '[(pull log [:xt/id])]
              :where [['log :type :log-line]
                      ['log :run-id run-id]]})
       (map first)
       (map :xt/id)))

(defn- runs-of
  [db-client group name]
  (->> (xt/q (xt/db db-client)
             {:find '[(pull run [:xt/id])]
              :where ['[run :type :pipeline-run]
                      ['run :group group]
                      ['run :name name]]})
       (map first)
       (map :xt/id)))

(defn delete
  "Deletes a pipeline along with its associated resources."
  [db-client {:keys [producer]} {:keys [group name]}]
  (log/infof "Deleting pipeline, runs and logs for (%s, %s)" group name)
  (f/try-all [id (keyword (format "bob.pipeline.%s/%s" group name))
              runs (runs-of db-client group name)
              logs (mapcat #(logs-of db-client %) runs)
              _ (xt/await-tx
                 db-client
                 (xt/submit-tx db-client (map #(vector ::xt/delete %) (concat logs runs [id]))))]
    (ev/emit producer
             {:type "Normal"
              :kind "Pipeline"
              :reason "PipelineDelete"
              :message (format "Pipeline %s/%s deleted with related data" group name)})
    (f/when-failed [err]
      (log/errorf "Pipeline deletion error: %s" (f/message err)))))
