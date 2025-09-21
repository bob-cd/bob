; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.pipeline
  (:require
   [clojure.tools.logging :as log]
   [common.events :as ev]
   [common.store :as store]
   [failjure.core :as f]))

(defn create
  "Creates a pipeline using the supplied data."
  [db {:keys [producer]} {:keys [group name] :as data}]
  (let [id (str "bob.pipeline/" group ":" name)]
    (log/infof "Creating pipeline %s with id %s" data id)
    (store/put db id data)
    (ev/emit producer
             {:type "Normal"
              :kind "Pipeline"
              :reason "PipelineCreate"
              :message (format "Pipeline created/updated %s/%s" group name)})))

(defn- runs-of
  [db group name]
  (->> (store/get db "bob.pipeline.run/" {:prefix true})
       (filter #(and (= group (:group (:value %)))
                     (= name (:name (:value %)))))
       (map :key)))

(defn delete
  "Deletes a pipeline along with its associated resources."
  [db {:keys [producer]} {:keys [group name]}]
  (log/infof "Deleting pipeline, runs and logs for (%s, %s)" group name)
  (f/try-all [runs (runs-of db group name)
              _ (run! #(store/delete db %) runs)
              _ (store/delete db (str "bob.pipeline/" group ":" name))]
    (ev/emit producer
             {:type "Normal"
              :kind "Pipeline"
              :reason "PipelineDelete"
              :message (format "Pipeline %s/%s deleted with related data" group name)})
    (f/when-failed [err]
      (log/errorf "Pipeline deletion error: %s" (f/message err)))))
