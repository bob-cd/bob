; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.handlers-test
  (:require
   [apiserver.handlers :as h]
   [apiserver.util :as u]
   [babashka.http-client :as http]
   [clojure.java.io :as io]
   [clojure.spec.alpha]
   [clojure.test :as t]
   [common.schemas]
   [failjure.core :as f]
   [langohr.channel :as lch]
   [xtdb.api :as xt])
  (:import
   [java.util Date]))

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
  (u/with-system
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
  (u/with-system
    (fn [db _ stream]
      (let [pipeline-id :bob.pipeline.test/test
            run-id :bob.pipeline.run/r-a-run]
        (t/testing "creation"
          (let [pipeline {:group "test"
                          :name "test"
                          :logger "logger-local"
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
                effect (xt/entity (xt/db db) pipeline-id)]
            (t/is (= pipeline-id (:xt/id effect)))
            (u/spec-assert :bob.db/pipeline effect)))
        (t/testing "deletion"
          (xt/await-tx
           db
           (xt/submit-tx db
                         [[::xt/put
                           {:xt/id run-id
                            :type :pipeline-run
                            :group "test"
                            :name "test"}]]))
          (let [_ (h/pipeline-delete {:parameters {:path {:group "test" :name "test"}}
                                      :db db
                                      :stream stream})
                pipeline-effect (xt/entity (xt/db db) pipeline-id)
                run-effect (xt/entity (xt/db db) run-id)]
            (t/is (nil? pipeline-effect))
            (t/is (nil? run-effect))))))))

(t/deftest pipeline-direct-tests
  (u/with-system
    (fn [db queue stream]
      (t/testing "invalid pipeline deletion with active runs"
        (xt/await-tx
         db
         (xt/submit-tx
          db
          [[::xt/put
            {:xt/id :bob.pipeline.run/r-1
             :type :pipeline-run
             :group "dev"
             :name "test"
             :status :running}]
           [::xt/put
            {:xt/id :bob.pipeline.run/r-2
             :type :pipeline-run
             :group "dev"
             :name "test"
             :status :passed}]
           [::xt/put
            {:xt/id :bob.logger/logger-local
             :type :logger
             :name "logger-local"
             :url "http://localhost:8002"}]]))
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
                        :logger "logger-local"
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
        (xt/await-tx
         db
         (xt/submit-tx
          db
          [[::xt/put
            {:xt/id :bob.pipeline.dev-paused/test-paused
             :type :pipeline
             :group "dev-paused"
             :name "test-paused"
             :image "busybox:musl"
             :logger "logger-local"
             :paused true
             :steps [{:cmd "echo yes"}]}]]))
        (let [{:keys [status body]} (h/pipeline-start {:parameters {:path {:group "dev-paused"
                                                                           :name "test-paused"}}
                                                       :queue queue
                                                       :db db
                                                       :stream stream})]
          (t/is (= 422 status))
          (t/is (= "Pipeline is paused. Unpause it first." (:message body))))))))

(t/deftest pipeline-pause-unpause
  (u/with-system
    (fn [db _ stream]
      (xt/await-tx
       db
       (xt/submit-tx
        db
        [[::xt/put
          {:xt/id :bob.pipeline.dev/test
           :type :pipeline
           :group "dev"
           :name "test"
           :image "busybox:musl"
           :logger "logger-local"
           :steps [{:cmd "echo yes"}]}]]))
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
  (u/with-system
    (fn [db _ _]
      (t/testing "pipeline logs"
        (let [payload "l1\nl2\nl3"]
          (xt/await-tx
           db
           (xt/submit-tx
            db
            [[::xt/put
              {:xt/id :bob.pipeline.dev/test
               :type :pipeline
               :group "test"
               :name "test"
               :image "busybox:musl"
               :logger "logger-local"
               :steps []}]
             [::xt/put
              {:xt/id :bob.logger/logger-local
               :type :logger
               :name "logger-local"
               :url "http://localhost:8002"}]]))
          (http/put "http://localhost:8002/bob_logs/test/test/r1"
                    {:body payload})
          (t/is (= payload
                   (-> {:db db
                        :parameters {:path {:group "test"
                                            :name "test"
                                            :id "r1"}}}
                       (h/pipeline-logs)
                       (:body)
                       (slurp)))))))))

(t/deftest pipeline-status-test
  (u/with-system
    (fn [db _ _]
      (t/testing "existing pipeline status"
        (xt/await-tx
         db
         (xt/submit-tx db
                       [[::xt/put
                         {:xt/id :bob.pipeline.run/r-1
                          :type :pipeline-run
                          :group "dev"
                          :name "test"
                          :status :running}]]))
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
  (u/with-system
    (fn [db _ _]
      (t/testing "fetching a valid artifact"
        (xt/await-tx
         db
         (xt/submit-tx
          db
          [[::xt/put
            {:xt/id :bob.artifact-store/local
             :type :artifact-store
             :name "local"
             :url "http://localhost:8001"}]]))
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
          (t/is (= "application/tar" (get headers "Content-Type")))))
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
  (u/with-system
    (fn [db _ _]
      (t/testing "listing pipelines"
        (xt/await-tx
         db
         (xt/submit-tx
          db
          [[::xt/put
            {:xt/id :bob.pipeline.dev/test1
             :type :pipeline
             :group "dev"
             :name "test1"
             :image "busybox:musl"
             :logger "logger-local"
             :steps [{:cmd "echo yes"}]}]
           [::xt/put
            {:xt/id :bob.pipeline.dev/test2
             :type :pipeline
             :group "dev"
             :name "test2"
             :logger "logger-local"
             :image "alpine:latest"
             :steps [{:cmd "echo yesnt"}]}]
           [::xt/put
            {:xt/id :bob.pipeline.prod/test1
             :type :pipeline
             :group "prod"
             :name "test1"
             :logger "logger-local"
             :image "alpine:latest"
             :steps [{:cmd "echo boo"}]}]]))
        (let [resp (h/pipeline-list {:db db
                                     :parameters {:query {:group "dev"}}})]
          (t/is (= #{{:group "dev"
                      :image "alpine:latest"
                      :name "test2"
                      :logger "logger-local"
                      :steps [{:cmd "echo yesnt"}]}
                     {:group "dev"
                      :image "busybox:musl"
                      :name "test1"
                      :logger "logger-local"
                      :steps [{:cmd "echo yes"}]}}
                   (-> resp
                       :body
                       :message
                       (set)))))))))

(t/deftest pipeline-runs-list-test
  (u/with-system
    (fn [db _ _]
      (t/testing "listing pipeline runs"
        (xt/await-tx
         db
         (xt/submit-tx
          db
          [[::xt/put
            {:xt/id :bob.pipeline.run/r-1
             :type :pipeline-run
             :group "dev"
             :name "test"
             :status :passed}]
           [::xt/put
            {:xt/id :bob.pipeline.run/r-2
             :type :pipeline-run
             :group "dev"
             :name "test"
             :status :failed}]]))
        (let [resp (h/pipeline-runs-list {:db db
                                          :parameters {:path {:group "dev"
                                                              :name "test"}}})]
          (t/is (= [{:run-id "r-1"
                     :status :passed}
                    {:run-id "r-2"
                     :status :failed}]
                   (-> resp
                       :body
                       :message))))))))

(t/deftest resource-provider-entities-test
  (u/with-system
    (fn [db _ stream]
      (let [id :bob.resource-provider/github]
        (t/testing "creation"
          (h/resource-provider-create {:parameters {:body {:name "github" :url "my-resource.com"}}
                                       :db db
                                       :stream stream})
          (let [effect (xt/entity (xt/db db) id)]
            (t/is (= id (:xt/id effect)))
            (u/spec-assert :bob.db/resource-provider effect)))
        (t/testing "deletion"
          (h/resource-provider-delete {:parameters {:path {:name "github"}}
                                       :db db
                                       :stream stream})
          (let [effect (xt/entity (xt/db db) id)]
            (t/is (nil? effect))))))))

(t/deftest resource-provider-test
  (u/with-system
    (fn [db _ _]
      (t/testing "resource-provider listing"
        (xt/await-tx
         db
         (xt/submit-tx db
                       [[::xt/put
                         {:xt/id :bob.resource-provider/test1
                          :type :resource-provider
                          :name "test1"
                          :url "http://localhost:8000"}]
                        [::xt/put
                         {:xt/id :bob.resource-provider/test2
                          :type :resource-provider
                          :name "test2"
                          :url "http://localhost:8001"}]]))
        (t/is (= [{:name "test1"
                   :url "http://localhost:8000"}
                  {:name "test2"
                   :url "http://localhost:8001"}]
                 (-> (h/resource-provider-list {:db db})
                     :body
                     :message)))))))

(t/deftest artifact-entities-test
  (u/with-system
    (fn [db _ stream]
      (let [id :bob.artifact-store/s3]
        (t/testing "creation"
          (h/artifact-store-create {:parameters {:body {:name "s3" :url "my-store.com"}}
                                    :db db
                                    :stream stream})
          (let [effect (xt/entity (xt/db db) id)]
            (t/is (= id (:xt/id effect)))
            (u/spec-assert :bob.db/artifact-store effect)))
        (t/testing "deletion"
          (h/artifact-store-delete {:parameters {:path {:name "s3"}}
                                    :db db
                                    :stream stream})
          (let [effect (xt/entity (xt/db db) id)]
            (t/is (nil? effect))))))))

(t/deftest artifact-store-test
  (u/with-system
    (fn [db _ _]
      (t/testing "artifact-store listing"
        (xt/await-tx
         db
         (xt/submit-tx db
                       [[::xt/put
                         {:xt/id :bob.artifact-store/test1
                          :type :artifact-store
                          :name "test1"
                          :url "http://localhost:8000"}]
                        [::xt/put
                         {:xt/id :bob.artifact-store/test2
                          :type :artifact-store
                          :name "test2"
                          :url "http://localhost:8001"}]]))
        (t/is (= [{:name "test1"
                   :url "http://localhost:8000"}
                  {:name "test2"
                   :url "http://localhost:8001"}]
                 (-> (h/artifact-store-list {:db db})
                     :body
                     :message)))))))

(t/deftest logger-entities-test
  (u/with-system
    (fn [db _ stream]
      (let [id :bob.logger/logger-local]
        (t/testing "creation"
          (h/logger-create {:parameters {:body {:name "logger-local" :url "my-logger.com"}}
                            :db db
                            :stream stream})
          (let [effect (xt/entity (xt/db db) id)]
            (t/is (= id (:xt/id effect)))
            (u/spec-assert :bob.db/logger effect)))
        (t/testing "deletion"
          (h/logger-delete {:parameters {:path {:name "logger-local"}}
                            :db db
                            :stream stream})
          (let [effect (xt/entity (xt/db db) id)]
            (t/is (nil? effect))))))))

(t/deftest logger-test
  (u/with-system
    (fn [db _ _]
      (t/testing "logger listing"
        (xt/await-tx
         db
         (xt/submit-tx db
                       [[::xt/put
                         {:xt/id :bob.logger/test1
                          :type :logger
                          :name "test1"
                          :url "http://localhost:8000"}]
                        [::xt/put
                         {:xt/id :bob.logger/test2
                          :type :logger
                          :name "test2"
                          :url "http://localhost:8001"}]]))
        (t/is (= [{:name "test1"
                   :url "http://localhost:8000"}
                  {:name "test2"
                   :url "http://localhost:8001"}]
                 (-> (h/logger-list {:db db})
                     :body
                     :message)))))))

(t/deftest raw-query-test
  (u/with-system
    (fn [db _ _]
      (t/testing "direct query"
        (xt/await-tx
         db
         (xt/submit-tx
          db
          [[::xt/put
            {:xt/id :food/biryani
             :type :indian}]]))
        (t/is
         (=
          "[[{\"type\":\"indian\"}]]"
          (:body
           (h/query
            {:db db
             :parameters
             {:query
              {:q "{:find [(pull f [:type])] :where [[f :type :indian]]})"}}})))))
      (t/testing "timed query"
        (let [point-in-time (Date/new)]
          (xt/await-tx
           db
           (xt/submit-tx
            db
            [[::xt/delete :food/biryani]]))
          (t/is
           (=
            "[[{\"type\":\"indian\"}]]"
            (:body
             (h/query
              {:db db
               :parameters
               {:query
                {:q "{:find [(pull f [:type])] :where [[f :type :indian]]})"
                 :t point-in-time}}})))))))))
