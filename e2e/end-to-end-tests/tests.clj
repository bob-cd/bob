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

(ns tests
  (:require [clojure.test :as t]
            [org.httpkit.client :as http]
            [clojure.set :as s]
            [cheshire.core :as json]
            [babashka.process :as p]
            [babashka.fs :as fs]))

(def bob-url "http://localhost:7777")

(def default-message "Initialized")

(def default-image-name "busybox:musl")

(def initial-step {:cmd (format "sh -c \"echo %s && sleep 1\"" default-message)})

(def default-pipeline
  {:steps [initial-step]
   :image default-image-name})

(def slow-pipeline
  {:steps [initial-step {:cmd "sleep 20"} {:cmd (str "echo done")}]
   :image default-image-name})
(def default-artifact-name "artifact-file")

(defn artifact-pipeline
  [store-context]
  {:steps [initial-step
           {:cmd               (format "sh -c \"echo 123 > /tmp/%s\"" default-artifact-name)
            :produces_artifact {:name  (:artifact-name store-context)
                                :path  (str "/tmp/" default-artifact-name)
                                :store (:store-name store-context)}}]

   :image default-image-name})

(defn generate-options
  [body]
  {:headers {"content-type" "application/json"}
   :body    (json/generate-string body)})

(def default-options
  (generate-options default-pipeline))

(def slow-options
  (generate-options slow-pipeline))

(def default-provider-name "resource-git")

(def default-provider-url "http://localhost:8000")

(defn timeout
  [timeout-ms callback]
  (let [fut (future (callback))
        ret (deref fut timeout-ms ::timed-out)]
    (when (= ret ::timed-out)
      (future-cancel fut))
    ret))

(defn parse-logs
  [pipeline-context]
  (get (json/parse-string (:body (:logs pipeline-context)))
       "message"))

(defn random-uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn get-resp-message
  [body]
  (:message (json/parse-string body true)))

(defn get-pipeline-status
  [{:keys [run-id]}]
  (let [{:keys [body]} @(http/get (format "%s/pipelines/status/runs/%s"
                                          bob-url
                                          run-id))
        message        (get-resp-message body)]
    message))

(defn pipeline-running?
  [pipeline-context]
  (= "running" (get-pipeline-status pipeline-context)))

(defn pipeline-has-status?
  [pipeline-context status]
  (let [actual-status (get-pipeline-status pipeline-context)]
    (= status actual-status)))

(defn pipeline-passed?
  [pipeline-context]
  (= "passed" (get-pipeline-status pipeline-context)))

(defn wait-until-true-timed
  [f]
  (let [response (f)]
    (when-not response
      (Thread/sleep 500)
      (recur f))))

(defn wait-until-true
  "Tries for 10 seconds to call f until it is true"
  [f]
  (timeout 10000 #(wait-until-true-timed f)))

(defn start-pipeline!
  "Starts a pipeline, waits until the pipeline has status 'running', then returns the run id"
  [{:keys [group name] :as pipeline-context}]
  (let [{:keys [body]}   @(http/post (format "%s/pipelines/start/groups/%s/names/%s"
                                             bob-url
                                             group
                                             name))
        pipeline-context (assoc pipeline-context :run-id (get-resp-message body))
        _                (wait-until-true #(pipeline-running? pipeline-context))]
    pipeline-context))

(def default-artifact-store-url "http://artifact:8001")
(defn make-artifact-options
  [{:keys [url]}]
  {:url url})

(defn list-artifact-stores
  "Returns a list of artifact stores like [{:url some-url :name some-name}]"
  []
  (-> @(http/get (format "%s/artifact-stores" bob-url))
      :body
      get-resp-message))

(defn artifact-store-exists?
  [{:keys [store-name]}]
  (contains? (set (map :name (list-artifact-stores)))
             store-name))

(defn create-artifact-store!
  "Creates an artifact store"
  []
  (let [store-context {:url           default-artifact-store-url
                       :store-name    (random-uuid)
                       :artifact-name default-artifact-name}]

    @(http/post (format "%s/artifact-stores/%s"
                        bob-url
                        (:store-name store-context))
                (generate-options (make-artifact-options store-context)))
    (wait-until-true #(artifact-store-exists? store-context))
    store-context))

(defn run-id?
  [s]
  (-> s
      (.substring 2)
      (java.util.UUID/fromString)
      (uuid?)))

(defn get-pipeline-runs
  "Lists pipeline runs by group and name"
  [{:keys [group name]}]
  (let [{:keys [body]} @(http/get (format "%s/pipelines/groups/%s/names/%s/runs"
                                          bob-url
                                          group
                                          name))]
    (get-resp-message body)))

(defn run-id-exists?
  [{:keys [run-id] :as pipeline-context}]
  (let [runs (get-pipeline-runs pipeline-context)]
    (some?
      (:run_id
        (first (filter
                 #(= run-id (:run_id %))
                 runs))))))

(defn get-pipelines
  "Lists existing pipelines"
  []
  (let [{:keys [body]} @(http/get (format "%s/pipelines" bob-url))]
    (get-resp-message body)))


(defn pipeline-exists?
  [{:keys [group name]}]
  (let [pipelines (get-pipelines)
        pipeline  (filter #(and (= (:group %) group)
                                (= (:name %) name))
                          pipelines)]
    (not-empty pipeline)))

(defn create-pipeline!
  "Returns the request body and status after creating a pipeline"
  [pipeline-context options]
  (let [{:keys [name group]}  pipeline-context
        {:keys [body status]} @(http/post (format "%s/pipelines/groups/%s/names/%s"
                                                  bob-url
                                                  group
                                                  name)
                                          options)
        _                     (wait-until-true #(pipeline-exists? pipeline-context))]
    {:body body :status status}))

(defn pipeline-started?
  "Returns true if a pipeline status is running or finished"
  [pipeline-context]
  (or (pipeline-has-status? pipeline-context "running")
      (pipeline-has-status? pipeline-context "passed")))

(defn get-pipeline-logs
  "Assocs :logs to pipeline context"
  [{:keys [run-id] :as pipeline-context}]
  (let [response @(http/get (format "%s/pipelines/logs/runs/%s/offset/%s/lines/%s"
                                    bob-url
                                    run-id
                                    0
                                    100))]
    (assoc pipeline-context :logs response)))

(defn pipeline-initialized?
  [pipeline-context]
  (s/subset? (set [default-message])
             (set (parse-logs (get-pipeline-logs pipeline-context)))))

(defn stop-pipeline-run
  "Stops pipeline, waits until side effect is done, and returns nil"
  [{:keys [group name run-id] :as pipeline-context}]
  @(http/post (str bob-url
                   "/pipelines/stop/groups/"
                   group
                   "/names/"
                   name
                   "/runs/"
                   run-id))
  (wait-until-true #(or (pipeline-has-status? pipeline-context "stopped")
                        (pipeline-has-status? pipeline-context "failed"))))

(defn pause-pipeline-run!
  "Pauses pipeline, waits until side effect is done, and returns nil"
  [{:keys [group name run-id] :as pipeline-context}]
  @(http/post (str bob-url
                   "/pipelines/pause/groups/"
                   group
                   "/names/"
                   name
                   "/runs/"
                   run-id))
  (wait-until-true #(pipeline-has-status? pipeline-context "paused"))
  pipeline-context)

(defn unpause-pipeline-run!
  "Unpauses pipeline, waits for side effect, returns nil"
  [{:keys [group name run-id] :as pipeline-context}]
  @(http/post (str bob-url
                   "/pipelines/unpause/groups/"
                   group
                   "/names/"
                   name
                   "/runs/"
                   run-id))
  (wait-until-true #(pipeline-has-status? pipeline-context "running"))
  pipeline-context)

(defn delete-pipeline!
  "Deletes pipeline, waits for side effect, returns nil"
  [{:keys [group name] :as pipeline-context}]
  @(http/delete (str bob-url
                     "/pipelines/groups/"
                     group
                     "/names/"
                     name))
  (wait-until-true #(not (pipeline-exists? pipeline-context)))
  pipeline-context)

(defn new-running-pipeline!
  "Creates a pipeline and starts it, waiting for the status to be 'running' before returning the pipeline
  context"
  ([] (new-running-pipeline! default-options))
  ([options]
   (let [name             (random-uuid)
         group            (random-uuid)
         _                (create-pipeline! {:name name :group group} options)
         pipeline-context (start-pipeline! {:name name :group group})]
     pipeline-context)))

(defn delete-artifact-store!
  [{:keys [store-name] :as store-context}]
  @(http/delete (format "%s/artifact-stores/%s"
                        bob-url
                        store-name))
  (wait-until-true #(not (artifact-store-exists? store-context))))

(defn get-pipeline-artifacts
  [{:keys [group name run-id]} {:keys [store-name artifact-name]}]
  @(http/get (format "%s/pipelines/groups/%s/names/%s/runs/%s/artifact-stores/%s/artifact/%s"
                     bob-url
                     group
                     name
                     run-id
                     store-name
                     artifact-name)))

(t/deftest health-check-test
  (t/testing "testing the health check endpoint"
    (let [{:keys [body status]} @(http/get "http://localhost:7777/can-we-build-it")]
      (t/is (= 200 status))
      (t/is (= "Yes we can! 🔨 🔨" (get-resp-message body))))))

(t/deftest api-test
  (t/testing "gets api spec as yaml"
    (let [{:keys [headers status]} @(http/get (format "%s/api.yaml" bob-url))]
      (t/is (= 200 status))
      (t/is (= "application/yaml" (:content-type headers))))))

(t/deftest resource-providers-test
  (t/testing "can add resources"
    (let [options               {:headers {"content-type" "application/json"}
                                 :body
                                 (json/generate-string {:url default-provider-url})}
          {:keys [body status]}
          @(http/post (format "%s/resource-providers/%s"
                              bob-url
                              default-provider-name)
                      options)]
      (Thread/sleep 500)
      (t/is (= 202 status))
      (t/is (= "Ok" (get-resp-message body)))))

  (t/testing "can list resource providers"
    (let [{:keys [body status]} @(http/get (format "%s/resource-providers" bob-url))]
      (t/is (= 200 status))
      (t/is (= [{:name default-provider-name :url default-provider-url}] (get-resp-message body)))))

  (t/testing "can remove resource providers"
    (let [options               {:headers {"content-type" "application/json"}}
          {:keys [body status]} @(http/delete (format "%s/resource-providers/%s"
                                                      bob-url
                                                      default-provider-name)
                                              options)]
      (t/is (= 202 status))
      (t/is (= "Ok" (get-resp-message body))))))


(t/deftest pipeline-test
  (t/testing "creates a pipeline and can prove the pipeline exists by listing it"
    (let [name             (random-uuid)
          group            (random-uuid)
          {:keys [status]} (create-pipeline! {:group group :name name} default-options)]
      (t/is (= 202 status))
      (t/is (pipeline-exists? {:group group :name name}))))

  (t/testing "starts a pipeline"
    (let [pipeline-context (new-running-pipeline!)]
      (t/is (pipeline-started? pipeline-context))))

  (t/testing "gets the logs of a pipeline"
    (let [pipeline-context (new-running-pipeline!)
          _                (wait-until-true #(pipeline-initialized? pipeline-context))
          pipeline-context (get-pipeline-logs pipeline-context)
          logs             (parse-logs pipeline-context)]
      (t/is (s/subset? (set [default-message]) (set logs)))))

  (t/testing "fetches a pipeline status"
    (let [pipeline-context (new-running-pipeline! slow-options)]
      (wait-until-true #(pipeline-initialized? pipeline-context))
      (t/is (= "running" (get-pipeline-status pipeline-context)))))

  (t/testing "fetches a list of pipelines runs"
    (let [pipeline-context (new-running-pipeline! slow-options)
          runs             (get-pipeline-runs pipeline-context)]
      (t/is (seq runs))))

  (t/testing "fetches a list of pipelines"
    (new-running-pipeline! slow-options)
    (t/is (get-pipelines)))

  (t/testing "stops a pipeline"
    (let [pipeline-context (new-running-pipeline! slow-options)
          _                (stop-pipeline-run pipeline-context)]
      (t/is (= "stopped" (get-pipeline-status pipeline-context)))))

  (t/testing "pauses a pipeline and can unpause it"
    (let [pipeline-context (new-running-pipeline! slow-options)]
      (wait-until-true #(pipeline-initialized? pipeline-context))
      (pause-pipeline-run! pipeline-context)
      (t/is (= "paused" (get-pipeline-status pipeline-context)))
      (unpause-pipeline-run! pipeline-context)
      (t/is (= "running" (get-pipeline-status pipeline-context)))))

  (t/testing "deletes a pipeline"
    (let [pipeline-context (new-running-pipeline! slow-options)]
      (wait-until-true #(pipeline-initialized? pipeline-context))
      (pause-pipeline-run! pipeline-context)
      (delete-pipeline! pipeline-context)
      (t/is (not (pipeline-exists? pipeline-context)))
      #_(t/is (= "Cannot find status" (get-pipeline-status pipeline-context)))))

  (t/testing "fetches an artifact produced by a pipeline run"
    (let [store-context    (create-artifact-store!)
          pipeline-context (new-running-pipeline! (generate-options (artifact-pipeline store-context)))
          _                (wait-until-true #(pipeline-has-status? pipeline-context "passed"))
          artifacts        (get-pipeline-artifacts pipeline-context store-context)]
      (->> artifacts
           :body
           slurp
           (spit "output.tar"))
      (p/sh '[tar -xf output.tar])
      (t/is (= "123" (.trim (slurp default-artifact-name))))
      (fs/delete "output.tar"))))

(t/deftest artifact-test
  (t/testing "creates an artifact and lists them"
    (let [store-context (create-artifact-store!)]
      (t/is (artifact-store-exists? store-context))))

  (t/testing "deletes an artifact store"
    (let [store-context (create-artifact-store!)]
      (t/is (artifact-store-exists? store-context))
      (delete-artifact-store! store-context)
      (t/is (not (artifact-store-exists? store-context))))))
