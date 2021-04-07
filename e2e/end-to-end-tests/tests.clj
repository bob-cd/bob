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
            [cheshire.core :as json]))


(def bob-url "http://localhost:7777")
(def default-message "123")
(def default-image-name "busybox:latest")
(def default-pipeline
  {:steps [{:cmd (str "echo " default-message)}]
   :image default-image-name})
(def infinite-pipeline
  {:steps [{:cmd (str "while :; do echo " default-message "; sleep 1; done")}]
   :image default-image-name})
(def default-options
  {:headers {"content-type" "application/json"}
   :body    (json/generate-string default-pipeline)})
(def infinite-options
  {:headers {"content-type" "application/json"}
   :body    (json/generate-string infinite-pipeline)})
(def default-provider-name "resource-git")
(def default-provider-url "http://localhost:8000")

(defn random-uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn get-resp-message
  [body]
  (:message (json/parse-string body true)))

(defn pipeline-status
  [id]
  (let [{:keys [body]} @(http/get (format "%s/pipelines/status/runs/%s"
                                          bob-url
                                          id))
        message        (get-resp-message body)]
    message))

(defn pipeline-running?
  [id]
  (= "running" (pipeline-status id)))

(defn pipeline-has-status?
  [id status]
  (= status (pipeline-status id)))

(defn pipeline-passed?
  [id]
  (= "passed" (pipeline-status id)))

(defn wait-until-true
  [f]
  (let [response (f)]
    (when-not response
      (Thread/sleep 500)
      (recur f))))

(defn start-pipeline
  "Creates a pipeline, waits until the side effect is finished, then returns the run id"
  [pipeline-group pipeline-name]
  (let [{:keys [body]} @(http/post (format "%s/pipelines/start/groups/%s/names/%s"
                                           bob-url
                                           pipeline-group
                                           pipeline-name))
        run-id         (get-resp-message body)
        _              (wait-until-true #(pipeline-running? run-id))]
    run-id))

(defn run-id?
  [s]
  (-> s
      (.substring 2)
      (java.util.UUID/fromString)
      (uuid?)))

(defn run-id-exists?
  [pipeline-group pipeline-name run-id]
  (let [{:keys [body]} @(http/get (format "%s/pipelines/groups/%s/names/%s/runs"
                                          bob-url
                                          pipeline-group
                                          pipeline-name))]
    (some?
      (:run_id
        (first (filter
                 #(= run-id (:run_id %))
                 (get-resp-message body)))))))

(defn pipeline-exists?
  [pgroup pname]
  (let [{:keys [body]} @(http/get (format "%s/pipelines" bob-url))
        pipelines      (get-resp-message body)

        pipeline       (filter #(and (= (:group %) pgroup)
                                     (= (:name %) pname))
                               pipelines)]
    (not-empty pipeline)))

(defn create-pipeline
  "Returns true if pipeline matching group and name exists"
  [pgroup pname options]
  (let [{:keys [body status]} @(http/post (format "%s/pipelines/groups/%s/names/%s"
                                                  bob-url
                                                  pgroup
                                                  pname)
                                          options)
        _                     (wait-until-true #(pipeline-exists? pgroup pname))]
    {:body body :status status}))

(defn pipeline-started?
  "Returns true if a pipeline status is running or finished"
  [run-id]
  (or (pipeline-has-status? run-id "running")
      (pipeline-has-status? run-id "passed")))

(defn get-pipeline-logs
  [run-id]
  @(http/get (format "%s/pipelines/logs/runs/%s/offset/%s/lines/%s"
                     bob-url
                     run-id
                     0
                     100)))

(defn stop-pipeline-run
  "Stops pipeline, waits until side effect is done, and returns nil"
  [pgroup pname run-id]

  @(http/post (str bob-url
                   "/pipelines/stop/groups/"
                   pgroup
                   "/names/"
                   pname
                   "/runs/"
                   run-id))

  (wait-until-true #(pipeline-has-status? run-id "stopped")))


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
      (t/is (= 200 status))
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
      (t/is (= 200 status))
      (t/is (= "Ok" (get-resp-message body))))))

(t/deftest pipeline-test
  (t/testing "creates a pipeline"
    (let [pname            (random-uuid)
          pgroup           (random-uuid)
          {:keys [status]} (create-pipeline pgroup pname default-options)]
      (t/is (= 200 status))
      (t/testing "and can prove the pipeline exists by listing it"
        (t/is (pipeline-exists? pgroup pname)))))

  (t/testing "starts a pipeline"
    (let [pname  (random-uuid)
          pgroup (random-uuid)
          _      (create-pipeline pgroup pname default-options)
          run-id (start-pipeline pgroup pname)]
      (t/is (pipeline-started? run-id))))

  (t/testing "gets the logs of a pipeline"
    (let [pname                 (random-uuid)
          pgroup                (random-uuid)
          _                     (create-pipeline pgroup pname default-options)
          run-id                (start-pipeline pgroup pname)
          _                     (wait-until-true #(pipeline-has-status? run-id "passed"))
          {:keys [body status]} (get-pipeline-logs run-id)
          message               (get-resp-message body)]
      (t/is (s/subset? (set [default-message]) (set message)))
      (t/is (= 200 status))))

  (t/testing "stops a pipeline"
    (let [pgroup (random-uuid)
          pname  (random-uuid)
          _      (create-pipeline pgroup pname infinite-options)
          run-id (start-pipeline pgroup pname)
          _      (stop-pipeline-run pgroup pname run-id)]
      (t/is (= "stopped" (pipeline-status run-id))))))
