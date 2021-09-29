; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.pipeline
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [xtdb.api :as xt]
            [common.errors :as err]))

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
                   (assoc :xt/id id)
                   (assoc :type :pipeline))
        result (f/try*
                 (xt/submit-tx db-client [[::xt/put data]]))]
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
      (xt/submit-tx db-client [[::xt/delete id]]))
    "Ok"))
