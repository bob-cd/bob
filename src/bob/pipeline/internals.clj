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
  (:require [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [taoensso.timbre :as log]
            [bob.execution.internals :as e]
            [bob.util :as u]
            [bob.resource.core :as r]
            [bob.artifact.core :as artifact]
            [bob.states :as states]
            [bob.pipeline.db :as db]
            [bob.resource.db :as rdb]))

;; TODO: Simpler solution, see if it can be improved
(def ^:private images-produced (atom {}))

(defn mark-image-for-gc
  [image run-id]
  (let [images-of-build (get @images-produced run-id)
        new-images      (if (some #{image} images-of-build)
                          images-of-build
                          (cons image images-of-build))]
    (swap! images-produced
           assoc
           run-id
           (if (nil? new-images) (list image) new-images))))

(defn gc-images
  [run-id]
  (log/debugf "Deleting all images for run %s" run-id)
  (f/try* (run! #(docker/image-rm states/docker-conn %)
                (get @images-produced run-id)))
  (swap! images-produced dissoc run-id))

(defn update-pid
  "Sets the current container id in the runs tables.
  This is used to track the last executed container in logging as well as stopping.
  Returns pid or error if any."
  [pid run-id]
  (f/try-all [_ (log/debugf "Updating pid %s for run id %s"
                            pid
                            run-id)
              _ (db/update-runs states/db
                                {:pid pid
                                 :id  run-id})]
    pid
    (f/when-failed [err]
      (log/errorf "Failed to update pid: %s" (f/message err))
      err)))

(defn resourceful-step
  "Create a resource mounted image for a step if it needs it."
  [step pipeline image run-id]
  (if-let [resource (:needs_resource step)]
    (do (u/log-to-db (format "[bob] Fetching and mounting resource %s" resource)
                     run-id)
        (r/mounted-image-from (rdb/resource-by-pipeline states/db
                                                        {:name     resource
                                                         :pipeline pipeline})
                              pipeline
                              image))
    image))

(defn next-step
  "Generates the next container from a previously run container.
  Works by saving the last container state in a diffed image and
  creating a new container from it, thereby managing state externally.
  Returns the new container id or errors if any."
  [build-state step evars pipeline run-id]
  (f/try-all [_             (log/debugf "Commiting container: %s" (:id build-state))
              image         (docker/commit-container
                             states/docker-conn
                             (:id build-state)
                             (format "%s/%d" (:id build-state) (System/currentTimeMillis))
                             "latest"
                             (:cmd step))
              _             (mark-image-for-gc image run-id)
              _             (log/debug "Removing commited container")
              _             (docker/rm states/docker-conn (:id build-state))
              resource      (:needs_resource step)
              mounted       (:mounted build-state)
              mount-needed? (if (nil? resource)
                              false
                              (not (some #{resource} mounted)))
              image         (if mount-needed?
                              (resourceful-step step pipeline image run-id)
                              image)
              _             (mark-image-for-gc image run-id)
              result        {:id      (e/build image step evars)
                             :mounted mounted}
              _             (log/debugf "Built a resourceful container: %s" (:id build-state))]
    (if mount-needed?
      (update-in result [:mounted] conj resource)
      result)
    (f/when-failed [err]
      (log/errorf "Next step creation failed: %s" (f/message err))
      err)))

(defn exec-step
  "Reducer function to implement the sequential execution of steps.

  Used to reduce an initial state with the list of steps, executing
  them to the final state.

  Stops the reduce if the pipeline stop has been signalled or any
  step has a non-zero exit.

  Additionally uploads the artifact if produced in a step with the
  PWD as the prefix.

  Returns the next state or errors if any."
  [run-id evars pipeline number build-state step]
  (let [stopped? (f/try* (-> (db/run-stopped? states/db
                                              {:id run-id})
                             (:stopped)))]
    (if (or stopped?
            (f/failed? build-state))
      (reduced build-state)
      (f/try-all [_            (log/infof "Executing step %s in pipeline %s"
                                          step
                                          pipeline)
                  result       (next-step build-state step evars pipeline run-id)
                  id           (update-pid (:id result) run-id)
                  id           (let [result (e/run id run-id)]
                                 (when (f/failed? result)
                                   (log/debugf "Removing failed container %s" id)
                                   (docker/rm states/docker-conn id))
                                 result)
                  [group name] (clojure.string/split pipeline #":")
                  _            (when-let [artifact (:produces_artifact step)]
                                 (u/log-to-db (format "[bob] Uploading artifact %s" artifact) run-id)
                                 (artifact/upload-artifact group
                                                           name
                                                           number
                                                           artifact
                                                           id
                                                           (str (get-in (docker/inspect states/docker-conn id)
                                                                        [:Config :WorkingDir])
                                                                "/"
                                                                (:artifact_path step))
                                                           (:artifact_store step)))]
        {:id      id
         :mounted (:mounted result)}
        (f/when-failed [err]
          (log/errorf "Failed to exec step: %s with error %s" step (f/message err))
          err)))))

(defn next-build-number-of
  "Generates a sequential build number for a pipeline."
  [name]
  (f/try-all [result (last (db/pipeline-runs states/db {:pipeline name}))]
    (if (nil? result)
      1
      (inc (result :number)))
    (f/when-failed [err]
      (log/errorf "Error generating build number for %s: %s"
                  name
                  (f/message err))
      err)))

;; TODO: Avoid doing the first step separately. Do it in the reduce like a normal person.
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
    (future (f/try-all [_           (log/infof "Starting new run %d for %s" number pipeline)
                        _           (db/insert-run states/db
                                                   {:id       run-id
                                                    :number   number
                                                    :pipeline pipeline
                                                    :status   "running"})
                        _           (u/log-to-db (format "[bob] Pulling image %s" image) run-id)
                        _           (e/pull image)
                        _           (mark-image-for-gc image run-id)
                        image       (resourceful-step first-step pipeline image run-id)
                        _           (mark-image-for-gc image run-id)
                        id          (e/build image first-step evars)
                        id          (update-pid id run-id)
                        id          (let [result (e/run id run-id)]
                                      (when (f/failed? result)
                                        (log/debugf "Removing failed container %s" id)
                                        (docker/rm states/docker-conn id))
                                      result)
                        build-state (reduce (partial exec-step run-id evars pipeline number)
                                            {:id      id
                                             :mounted (if first-resource
                                                        [first-resource]
                                                        [])}
                                            (rest steps))
                        _           (log/debug "Removing last successful container")
                        _           (docker/rm states/docker-conn (:id build-state))
                        _           (log/infof "Marking run %d for %s as passed" number pipeline)
                        _           (gc-images run-id)
                        _           (db/update-run states/db
                                                   {:status "passed"
                                                    :id     run-id})
                        _           (u/log-to-db "[bob] Run successful" run-id)]
              id
              (f/when-failed [err]
                (log/infof "Marking run %d for %s as failed with reason: %s"
                           number
                           pipeline
                           (f/message err))
                (u/log-to-db (format "[bob] Run failed with reason: %s" (f/message err)) run-id)
                (gc-images run-id)
                (f/try* (db/update-run states/db
                                       {:status "failed"
                                        :id     run-id})))))))

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
        status   (f/try* (-> (db/status-of states/db criteria)
                             (:status)))]
    (when (= status "running")
      (log/debugf "Stopping run %d of pipeline %s" number name)
      (f/try-all [_      (db/stop-run states/db criteria)
                  pid    (-> (db/pid-of-run states/db criteria)
                             (:last_pid))
                  status (e/status-of pid)
                  _      (when (status :running?)
                           (log/debugf "Killing container %s" pid)
                           (e/kill-container pid)
                           (docker/rm states/docker-conn pid))
                  run-id (-> (db/run-id-of states/db criteria)
                             (:id))
                  _      (gc-images run-id)]
        "Ok"
        (f/when-failed [err]
          (log/errorf "Failed to stop pipeline: %s" (f/message err))
          (f/message err))))))

(defn pipeline-logs
  "Fetches all the logs from from a particular run-id split by lines.

  Can be paginated with offset and number of lines.

  Returns the list of lines or errors if any."
  [name number offset lines]
  (f/try-all [_      (log/debugf "Fetching logs for pipeline %s" name)
              run-id (-> (db/run-id-of states/db
                                       {:pipeline name
                                        :number   number})
                         (:id))
              logs   (:content (db/logs-of states/db {:run-id run-id}))
              _      (when (nil? logs)
                       (f/fail "Unable to fetch logs for this run"))]
    (->> logs
         (clojure.string/split-lines)
         (drop (dec offset))
         (take lines))
    (f/when-failed [err]
      (log/errorf "Failed to fetch logs for pipeline %s: %s" name (f/message err))
      err)))

(defn image-of
  "Returns the image associated with the pipeline."
  [pipeline]
  (let [image (-> (db/image-of states/db {:name pipeline})
                  (:image))]
    (if (nil? image)
      (f/fail "No such pipeline")
      image)))

(comment
  (resourceful-step {:needs_resource "source"
                     :cmd            "ls"}
                    "test:test"
                    "busybox:musl"
                    "run-id")

  (db/insert-run states/db
                 {:id       "1"
                  :number   "1"
                  :pipeline "dev:test"
                  :status   "running"})

  (next-build-number-of "dev:test")

  (resourceful-step (first (db/ordered-steps states/db {:pipeline "dev:test"}))
                    "dev:test"
                    "busybox:musl"
                    "run-id")

  (exec-steps "busybox:musl"
              (db/ordered-steps states/db {:pipeline "dev:test"})
              "dev:test"
              {}))
