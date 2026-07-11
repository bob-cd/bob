; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.pipeline
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.tools.logging :as log]
   [common.events :as ev]
   [common.schemas]
   [common.store :as store]
   [failjure.core :as f]))

(defn create
  "Creates a pipeline using the supplied data."
  [db {:keys [producer]} {:keys [group name] :as data}]
  (log/infof "Creating pipeline %s" data)
  (store/kv-put db "bob_pipeline" (str group ":" name) (spec/conform :bob/pipeline data))
  (ev/emit producer
           {:type "Normal"
            :kind "Pipeline"
            :reason "PipelineCreate"
            :message (format "Pipeline created/updated %s/%s" group name)}))

(defn- runs-of
  [db group name]
  (->> (store/kv-list db "bob_pipeline_run")
       (filter #(and (= group (:group (:value %)))
                     (= name (:name (:value %)))))
       (map :key)))

(defn delete
  "Deletes a pipeline along with its associated resources."
  [db {:keys [producer]} {:keys [group name]}]
  (log/infof "Deleting pipeline, runs and logs for (%s, %s)" group name)
  (f/try-all [runs (runs-of db group name)
              _ (run! #(store/kv-del db "bob_pipeline_run" %) runs)
              _ (store/kv-del db "bob_pipeline" (str group ":" name))]
    (ev/emit producer
             {:type "Normal"
              :kind "Pipeline"
              :reason "PipelineDelete"
              :message (format "Pipeline %s/%s deleted with related data" group name)})
    (f/when-failed [err]
      (log/errorf "Pipeline deletion error: %s" (f/message err)))))
