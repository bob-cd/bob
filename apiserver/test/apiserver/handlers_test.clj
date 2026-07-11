; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.handlers-test
  (:require
   [apiserver.handlers :as h]
   [babashka.http-client :as http]
   [clojure.java.io :as io]
   [clojure.spec.alpha]
   [clojure.test :as t]
   [common.schemas]
   [common.store :as store]
   [common.test-utils :as u]
   [failjure.core :as f]
   [langohr.channel :as lch]))

(t/deftest helpers-test
  (t/testing "default response"
    (t/is (= {:status 202
              :body {:message "response"}}
             (h/respond "response"))))
  (t/testing "response with status"
    (t/is (= {:status 404
              :body {:message "not found"}}
             (h/respond "not found" 404)))))

(t/deftest health-check-test
  (u/with-apiserver-system
    (fn [db queue _]
      (t/testing "passing health check"
        (let [{:keys [status body]} (h/health-check {:db db
                                                     :queue queue})]
          (t/is (= 200 status))
          (t/is (= "Yes we can! 🔨 🔨"
                   (-> body
                       :message
                       f/message)))))
      (t/testing "failing health check"
        (lch/close queue)
        (let [{:keys [status body]} (h/health-check {:db db
                                                     :queue queue})]
          (t/is (= 500 status))
          (t/is (= "Queue is unreachable"
                   (-> body
                       :message
                       first
                       f/message))))))))

(t/deftest pipeline-entities-test
  (u/with-apiserver-system
    (fn [db _ stream]
      (t/testing "creation"
        (let [pipeline {:group "test"
                        :name "test"
                        :steps [{:cmd "echo hello"}
                                {:needs_resource "source"
                                 :cmd "ls"}
                                {:cmd "touch test"
                                 :produces_artifact {:name "file"
                                                     :path "test"
                                                     :store "s3"}}]
                        :vars {:k1 "v1"
                               :k2 "v2"}
                        :resources [{:name "source"
                                     :type "external"
                                     :provider "git"
                                     :params {:repo "https://github.com/bob-cd/bob"
                                              :branch "main"}}
                                    {:name "source2"
                                     :type "external"
                                     :provider "git"
                                     :params {:repo "https://github.com/lispyclouds/contajners"
                                              :branch "main"}}]
                        :image "busybox:musl"}
              _ (h/pipeline-create {:parameters {:body pipeline}
                                    :db db
                                    :stream stream})
              effect (store/kv-get db "bob_pipeline" "test:test")]
          (t/is (= "test" (:group effect)))
          (t/is (= "test" (:name effect)))
          (u/spec-assert :bob/pipeline effect))
        (t/testing "deletion"
          (store/kv-put db "bob_pipeline_run" "r-a-run" {:group "test" :name "test"})
          (let [_ (h/pipeline-delete {:parameters {:path {:group "test" :name "test"}}
                                      :db db
                                      :stream stream})
                pipeline-effect (store/kv-get db "bob_pipeline" "test:test")
                run-effect (store/kv-get db "bob_pipeline_run" "r-a-run")]
            (t/is (nil? pipeline-effect))
            (t/is (nil? run-effect))))))))

(t/deftest pipeline-direct-tests
  (u/with-apiserver-system
    (fn [db queue stream]
      (t/testing "invalid pipeline deletion with active runs"
        (store/kv-put db
                      "bob_pipeline_run"
                      "r-1"
                      {:group "dev"
                       :name "test"
                       :logger "logger-local"
                       :status :running})
        (store/kv-put db
                      "bob_pipeline_run"
                      "r-2"
                      {:group "dev"
                       :name "test"
                       :logger "logger-local"
                       :status :passed})
        (store/kv-put db
                      "bob_logger"
                      "logger-local"
                      {:name "logger-local"
                       :url "http://localhost:8002"})
        (let [{:keys [status body]} (h/pipeline-delete {:parameters {:path {:group "dev" :name "test"}}
                                                        :db db
                                                        :queue queue
                                                        :stream stream})]
          (t/is (= 422 status))
          (t/is (= {:error "Pipeline has active runs. Wait for them to finish or stop them."
                    :runs ["r-1"]}
                   (:message body)))))
      (t/testing "pipeline start"
        (let [pipeline {:group "dev"
                        :name "test"
                        :steps [{:cmd "echo hello"}]
                        :image "busybox:musl"}
              _ (h/pipeline-create {:parameters {:body pipeline}
                                    :db db
                                    :stream stream})
              {:keys [status]} (h/pipeline-start {:parameters {:path {:group "dev" :name "test"}}
                                                  :queue queue
                                                  :db db
                                                  :stream stream})]
          (t/is (= 202 status))))
      (t/testing "starting paused pipeline"
        (store/kv-put db
                      "bob_pipeline"
                      "dev-paused:test-paused"
                      {:group "dev-paused"
                       :name "test-paused"
                       :image "busybox:musl"
                       :paused true
                       :steps [{:cmd "echo yes"}]})
        (let [{:keys [status body]} (h/pipeline-start {:parameters {:path {:group "dev-paused"
                                                                           :name "test-paused"}}
                                                       :queue queue
                                                       :db db
                                                       :stream stream})]
          (t/is (= 422 status))
          (t/is (= "Pipeline is paused. Unpause it first." (:message body))))))))

(t/deftest pipeline-pause-unpause
  (u/with-apiserver-system
    (fn [db _ stream]
      (store/kv-put db
                    "bob_pipeline"
                    "dev:test"
                    {:group "dev"
                     :name "test"
                     :image "busybox:musl"
                     :steps [{:cmd "echo yes"}]})
      (t/testing "pipeline pause"
        (h/pipeline-pause-unpause true
                                  {:parameters {:path {:group "dev"
                                                       :name "test"}}
                                   :db db
                                   :stream stream})
        (t/is (:paused (h/pipeline-data db "dev" "test"))))
      (t/testing "pipeline unpause"
        (h/pipeline-pause-unpause false
                                  {:parameters {:path {:group "dev"
                                                       :name "test"}}
                                   :db db
                                   :stream stream})
        (t/is (not (:paused (h/pipeline-data db "dev" "test"))))))))

(t/deftest pipeline-logs-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "pipeline logs"
        (let [payload "l1\nl2\nl3"]
          (store/kv-put db
                        "bob_pipeline_run"
                        "r1"
                        {:group "dev"
                         :name "test"
                         :logger "logger-local"
                         :status :passed})
          (store/kv-put db
                        "bob_logger"
                        "logger-local"
                        {:name "logger-local"
                         :url "http://localhost:8002"})
          (http/put "http://localhost:8002/bob_logs/r1"
                    {:body payload})
          (t/is (= payload
                   (-> {:db db
                        :parameters {:path {:id "r1"}}}
                       (h/pipeline-logs)
                       (:body)
                       (slurp)))))))))

(t/deftest pipeline-status-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "existing pipeline status"
        (store/kv-put db
                      "bob_pipeline_run"
                      "r-1"
                      {:group "dev"
                       :name "test"
                       :logger "logger-local"
                       :status :running})
        (t/is (= "running"
                 (-> (h/pipeline-status {:db db
                                         :parameters {:path {:run-id "r-1"}}})
                     :body
                     :message))))
      (t/testing "non-existing pipeline status"
        (let [{:keys [status body]}
              (h/pipeline-status {:db db
                                  :parameters {:path {:run-id "r-2"}}})]
          (t/is (= 404 status))
          (t/is (= "Cannot find status"
                   (:message body))))))))

(t/deftest pipeline-artifact-fetch-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "fetching a valid artifact"
        (store/kv-put db
                      "bob_artifact-store"
                      "local"
                      {:name "local"
                       :url "http://localhost:8001"})
        (http/post "http://localhost:8001/bob_artifact/dev/test/a-run-id/file"
                   {:body (io/input-stream "test/test.tar")})
        (let [{:keys [status headers]}
              (h/pipeline-artifact {:db db
                                    :parameters {:path {:group "dev"
                                                        :name "test"
                                                        :id "a-run-id"
                                                        :store-name "local"
                                                        :artifact-name "file"}}})]
          (t/is (= 200 status))
          (t/is (= "application/octet-stream" (get headers "Content-Type")))))
      (t/testing "unregistered artifact store"
        (let [{:keys [status body]}
              (h/pipeline-artifact {:db db
                                    :parameters {:path {:group "dev"
                                                        :name "test"
                                                        :id "a-run-id"
                                                        :store-name "nope"
                                                        :artifact-name "file"}}})]
          (t/is (= 400 status))
          (t/is (= "Cannot locate artifact store nope" (:message body)))))
      (t/testing "non-existing artifact"
        (let [{:keys [status]}
              (h/pipeline-artifact {:db db
                                    :parameters {:path {:group "dev"
                                                        :name "test"
                                                        :id "a-run-id"
                                                        :store-name "local"
                                                        :artifact-name "yes"}}})]
          (t/is (= 400 status)))))))

(t/deftest pipeline-list-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "listing pipelines"
        (store/kv-put db
                      "bob_pipeline"
                      "dev:test1"
                      {:group "dev"
                       :name "test1"
                       :image "busybox:musl"
                       :steps [{:cmd "echo yes"}]})
        (store/kv-put db
                      "bob_pipeline"
                      "dev:test2"
                      {:group "dev"
                       :name "test2"
                       :image "alpine:latest"
                       :steps [{:cmd "echo yesnt"}]})
        (store/kv-put db
                      "bob_pipeline"
                      "prod:test1"
                      {:group "prod"
                       :name "test1"
                       :image "alpine:latest"
                       :steps [{:cmd "echo boo"}]})
        (let [resp (h/pipeline-list {:db db
                                     :parameters {:query {:group "dev"}}})]
          (t/is (= #{{:group "dev"
                      :image "alpine:latest"
                      :name "test2"
                      :steps [{:cmd "echo yesnt"}]}
                     {:group "dev"
                      :image "busybox:musl"
                      :name "test1"
                      :steps [{:cmd "echo yes"}]}}
                   (-> resp
                       :body
                       :message
                       (set)))))))))

(t/deftest pipeline-runs-list-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "listing pipeline runs"
        (store/kv-put db
                      "bob_pipeline_run"
                      "r-1"
                      {:group "dev"
                       :name "test"
                       :logger "logger-local"
                       :status :passed})
        (store/kv-put db
                      "bob_pipeline_run"
                      "r-2"
                      {:group "dev"
                       :name "test"
                       :logger "logger-local"
                       :status :failed})
        (let [resp (h/pipeline-runs-list {:db db
                                          :parameters {:path {:group "dev"
                                                              :name "test"}}})]
          (t/is (= #{{:run-id "r-1"
                      :status :passed
                      :logger "logger-local"}
                     {:run-id "r-2"
                      :status :failed
                      :logger "logger-local"}}
                   (-> resp
                       :body
                       :message
                       (set)))))))))

(t/deftest resource-provider-entities-test
  (u/with-apiserver-system
    (fn [db _ stream]
      (t/testing "creation"
        (h/resource-provider-create {:parameters {:body {:name "github" :url "my-resource.com"}}
                                     :db db
                                     :stream stream})
        (let [effect (store/kv-get db "bob_resource-provider" "github")]
          (t/is (= "my-resource.com" (:url effect)))
          (u/spec-assert :bob/resource-provider effect)))
      (t/testing "deletion"
        (h/resource-provider-delete {:parameters {:path {:name "github"}}
                                     :db db
                                     :stream stream})
        (let [effect (store/kv-get db "bob_resource-provider" "github")]
          (t/is (nil? effect)))))))

(t/deftest resource-provider-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "resource-provider listing"
        (store/kv-put db
                      "bob_resource-provider"
                      "test1"
                      {:name "test1"
                       :url "http://localhost:8000"})
        (store/kv-put db
                      "bob_resource-provider"
                      "test2"
                      {:name "test2"
                       :url "http://localhost:8001"})
        (t/is (= #{{:name "test1"
                    :url "http://localhost:8000"}
                   {:name "test2"
                    :url "http://localhost:8001"}}
                 (->> (h/resource-provider-list {:db db})
                      :body
                      :message
                      (into #{}))))))))

(t/deftest artifact-entities-test
  (u/with-apiserver-system
    (fn [db _ stream]
      (t/testing "creation"
        (h/artifact-store-create {:parameters {:body {:name "s3" :url "my-store.com"}}
                                  :db db
                                  :stream stream})
        (let [effect (store/kv-get db "bob_artifact-store" "s3")]
          (t/is (= "my-store.com" (:url effect)))
          (u/spec-assert :bob/artifact-store effect)))
      (t/testing "deletion"
        (h/artifact-store-delete {:parameters {:path {:name "s3"}}
                                  :db db
                                  :stream stream})
        (let [effect (store/kv-get db "bob_artifact-store" "s3")]
          (t/is (nil? effect)))))))

(t/deftest artifact-store-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "artifact-store listing"
        (store/kv-put db
                      "bob_artifact-store"
                      "test1"
                      {:name "test1"
                       :url "http://localhost:8000"})
        (store/kv-put db
                      "bob_artifact-store"
                      "test2"
                      {:name "test2"
                       :url "http://localhost:8001"})
        (t/is (= #{{:name "test1"
                    :url "http://localhost:8000"}
                   {:name "test2"
                    :url "http://localhost:8001"}}
                 (->> (h/artifact-store-list {:db db})
                      :body
                      :message
                      (into #{}))))))))

(t/deftest logger-entities-test
  (u/with-apiserver-system
    (fn [db _ stream]
      (t/testing "creation"
        (h/logger-create {:parameters {:body {:name "logger-local" :url "my-logger.com"}}
                          :db db
                          :stream stream})
        (let [effect (store/kv-get db "bob_logger" "logger-local")]
          (t/is (= "my-logger.com" (:url effect)))
          (u/spec-assert :bob/logger effect)))
      (t/testing "deletion"
        (h/logger-delete {:parameters {:path {:name "logger-local"}}
                          :db db
                          :stream stream})
        (let [effect (store/kv-get db "bob_logger" "logger-local")]
          (t/is (nil? effect)))))))

(t/deftest logger-test
  (u/with-apiserver-system
    (fn [db _ _]
      (t/testing "logger listing"
        (store/kv-put db
                      "bob_logger"
                      "test1"
                      {:name "test1"
                       :url "http://localhost:8000"})
        (store/kv-put db
                      "bob_logger"
                      "test2"
                      {:name "test2"
                       :url "http://localhost:8001"})
        (t/is (= #{{:name "test1"
                    :url "http://localhost:8000"}
                   {:name "test2"
                    :url "http://localhost:8001"}}
                 (->> (h/logger-list {:db db})
                      :body
                      :message
                      (into #{}))))))))
