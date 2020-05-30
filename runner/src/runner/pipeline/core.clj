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

(ns runner.pipeline.core
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [runner.errors :as err]
            [runner.pipeline.db :as db])
  (:import [java.util UUID]))

(defn- next-build-number-of
  "Generates a sequential build number for a pipeline."
  [db-conn name]
  (let [result (f/try* (last (db/pipeline-runs db-conn {:pipeline name})))]
    (if (or (f/failed? result) (nil? result))
      1
      (inc (result :number)))))

;; TODO: Avoid doing the first step separately. Do it in the reduce like a normal person.
(defn- exec-steps
  "Implements the sequential execution of the list of steps with a starting image.

  Dispatches asynchronously and uses a composition of the above functions.
  Makes an accumulator of current id and the mounted resources

  Returns the final id or errors if any."
  [db-conn image [step & steps] pipeline evars]
  (let [run-id         (str (UUID/randomUUID))
        first-resource (:needs_resource step)
        number         (next-build-number-of db-conn pipeline)]
    (future (f/try-all [_ (log/infof "Starting new run %d for %s"
                                     number
                                     pipeline)
                        _ (db/insert-run db-conn
                                         {:id       run-id
                                          :number   number
                                          :pipeline pipeline
                                          :status   "running"})]))))

(defn start
  "Asynchronously starts a pipeline in a group by name."
  [db-conn queue-chan {:keys [group name]}]
  (f/try-all [pipeline (str group ":" name)
              image    (:image (db/image-of db-conn {:name pipeline}))
              steps    (db/ordered-steps db-conn {:pipeline pipeline})
              vars     (->> (db/evars-by-pipeline db-conn {:pipeline pipeline})
                            (map #(hash-map (keyword (:key %)) (:value %)))
                            (into {}))]
    (do
      (log/infof "Starting pipeline %s" pipeline)
      (exec-steps db-conn image steps pipeline vars)
      "Ok")
    (f/when-failed [err]
      (err/publish-error queue-chan (format "Error starting pipeline: %s" (f/message err))))))
