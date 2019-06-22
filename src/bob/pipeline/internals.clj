;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.pipeline.internals
  (:require [clojure.core.async :as a]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.execution.internals :as e]
            [bob.util :as u]
            [bob.resource.core :as r]
            [bob.artifact.core :as artifact]
            [bob.states :as states]
            [bob.pipeline.db :as db]
            [bob.resource.db :as rdb]))

;; TODO: Reduce and optimize DB interactions to a single place

(defn update-pid
  "Sets the current container id to both logs and runs tables.
  This is used to track the last executed container in logging as well as stopping.
  Returns pid or error if any."
  [pid run-id]
  (f/attempt-all [_ (u/unsafe! (db/insert-log-entry states/db
                                                    {:pid pid
                                                     :run run-id}))
                  _ (u/unsafe! (db/update-runs states/db
                                               {:pid pid
                                                :id  run-id}))]
    pid
    (f/when-failed [err] err)))

(defn- resourceful-step
  "Create a resource mounted image for a step if it needs it."
  [step pipeline image]
  (if (:needs_resource step)
    (r/mounted-image-from (rdb/resource-by-pipeline states/db
                                                    {:name     (:needs_resource step)
                                                     :pipeline pipeline})
                          pipeline
                          image)
    image))

;; TODO: Extra container is created here with resources, see if can be avoided.
(defn- next-step
  "Generates the next container from a previously run container.
  Works by saving the last container state in a diffed image and
  creating a new container from it, thereby managing state externally.
  Returns the new container id or errors if any."
  [id step evars pipeline]
  (f/attempt-all [image         (u/unsafe! (docker/commit-container
                                             states/docker-conn
                                             (:id id)
                                             (format "%s/%d" (:id id) (System/currentTimeMillis))
                                             "latest"
                                             (:cmd step)))
                  resource      (:needs_resource step)
                  mounted       (:mounted id)
                  mount-needed? (not (some #{resource} mounted))
                  image         (if mount-needed?
                                  (resourceful-step step pipeline image)
                                  image)
                  result        {:id      (e/build image step evars)
                                 :mounted mounted}]
    (if mount-needed?
      (update-in result [:mounted] conj resource)
      result)
    (f/when-failed [err] err)))

(defn- exec-step
  "Reducer function to implement the sequential execution of steps.

  Used to reduce an initial state with the list of steps, executing
  them to the final state.

  Stops the reduce if the pipeline stop has been signalled or any
  non-zero step outcome.

  Additionally uploads the artifact if produced in a step with the
  PWD as the prefix.

  Returns the next state or errors if any."
  [run-id evars pipeline number id step]
  (let [stopped? (u/unsafe! (-> (db/run-stopped? states/db
                                                 {:id run-id})
                                (:stopped)))]
    (if (or stopped?
            (f/failed? (:id id)))
      (reduced id)
      (f/attempt-all [result       (next-step id step evars pipeline)
                      id           (update-pid (:id result) run-id)
                      id           (e/run id)
                      [group name] (clojure.string/split pipeline #":")
                      _            (if-let [artifact (:produces_artifact step)]
                                     (artifact/upload-artifact group
                                                               name
                                                               number
                                                               artifact
                                                               id
                                                               (str (get-in (docker/inspect states/docker-conn id)
                                                                            [:Config :WorkingDir])
                                                                    "/"
                                                                    (:artifact_path step))))]
        {:id      id
         :mounted (:mounted result)}
        (f/when-failed [err] err)))))

(defn- next-build-number-of
  "Generates a sequential build number for a pipeline."
  [name]
  (f/attempt-all [result (u/unsafe! (last (db/pipeline-runs states/db {:pipeline name})))]
    (if (nil? result)
      1
      (inc (result :number)))
    (f/when-failed [err] err)))

(defn exec-steps
  "Implements the sequential execution of the list of steps with a starting image.

  - Dispatches asynchronously and uses a composition of the above functions.
  - Takes and accumulator of current id and the mounted resources

  Returns the final id or errors if any."
  [image steps pipeline evars]
  (let [run-id         (u/get-id)
        first-step     (first steps)
        first-resource (:needs_resource first-step)
        number         (next-build-number-of pipeline)]
    (a/go (f/attempt-all [_     (u/unsafe! (db/insert-run states/db
                                                          {:id       run-id
                                                           :number   number
                                                           :pipeline pipeline
                                                           :status   "running"}))
                          image (e/pull image)
                          image (resourceful-step first-step pipeline image)
                          id    (f/ok-> (e/build image first-step evars)
                                        (update-pid run-id)
                                        (e/run))
                          id    (reduce (partial exec-step run-id evars pipeline number)
                                        {:id      id
                                         :mounted (if first-resource
                                                    [first-resource]
                                                    [])}
                                        (rest steps))
                          _     (u/unsafe! (db/update-run states/db
                                                          {:status "passed"
                                                           :id     run-id}))]
            id
            (f/when-failed [err]
              (do (u/unsafe! (db/update-run states/db
                                            {:status "failed"
                                             :id     run-id}))
                  (f/message err)))))))

(defn stop-pipeline
  "Stops a pipeline if running.
  Performs the following:
  - Sets the field *stopped* in the runs table to true such that exec-step won't reduce any further.
  - Gets the *last_pid* field from the runs table.
  - Checks if that container with that id is running; if yes, kills it.
  Returns Ok or any errors if any."
  [name number]
  (let [criteria {:pipeline name
                  :number   number}
        status   (u/unsafe! (-> (db/status-of states/db criteria)
                                (:status)))]
    (when (= status "running")
      (f/attempt-all [_      (u/unsafe! (db/stop-run states/db criteria))
                      pid    (u/unsafe! (-> (db/pid-of-run states/db criteria)
                                            (:last_pid)))
                      status (e/status-of pid)
                      _      (when (status :running?)
                               (e/kill-container pid))]
        "Ok"
        (f/when-failed [err] (f/message err))))))

(defn pipeline-logs
  "Aggregates and reads the logs from all of the containers in a pipeline.
  Performs the following:
  - Fetch the run UUID of a pipeline of a group.
  - Fetch all container ids associated with that UUID.
  - Lazily read the streams from each and append and truncate the lines.
  Returns the list of lines or errors if any."
  [name number offset lines]
  (f/attempt-all [run-id     (u/unsafe! (-> (db/run-id-of states/db
                                                          {:pipeline name
                                                           :number   number})
                                            (:id)))
                  containers (u/unsafe! (->> (db/container-ids states/db
                                                              {:run-id run-id})
                                             (map #(:pid %))))]
    (->> containers
         (map #(e/log-stream-of %))
         (filter #(not (nil? %)))
         (flatten)
         (drop (dec offset))
         (take lines))
    (f/when-failed [err] (f/message err))))

(defn image-of
  "Returns the image associated with the pipeline."
  [pipeline]
  (u/unsafe! (-> (db/image-of states/db {:name pipeline})
                 (:image))))

(comment
  (resourceful-step {:needs_resource "source"
                     :cmd            "ls"}
                    "test:test"
                    "busybox:musl")

  (db/insert-run states/db
                 {:id       "1"
                  :number   "1"
                  :pipeline "dev:test"
                  :status   "running"})

  (next-build-number-of "dev:test")

  (resourceful-step (first (db/ordered-steps states/db {:pipeline "dev:test"}))
                    "dev:test"
                    "busybox:musl")

  (exec-steps "busybox:musl"
              (->>(db/ordered-steps states/db {:pipeline "dev:test"})
                  (map #(update-in % [:cmd] u/clob->str)))
              "dev:test"
              {}))
