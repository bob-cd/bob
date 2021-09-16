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
                   (assoc :xt.db/id id)
                   (assoc :type :pipeline))
        result (f/try*
                 (xt/submit-tx db-client [[:xt.tx/put data]]))]
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
      (xt/submit-tx db-client [[:xt.tx/delete id]]))
    "Ok"))
