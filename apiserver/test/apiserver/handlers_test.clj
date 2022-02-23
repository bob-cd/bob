; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.handlers-test
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [failjure.core :as f]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [langohr.queue :as lq]
            [clojure.data.json :as json]
            [xtdb.api :as xt]
            [java-http-clj.core :as http]
            [common.schemas]
            [apiserver.handlers :as h]
            [apiserver.util :as u])
  (:import [java.time Instant]))

(t/deftest helpers-test
  (t/testing "default response"
    (t/is (= {:status 202
              :body   {:message "response"}}
             (h/respond "response"))))
  (t/testing "response with status"
    (t/is (= {:status 404
              :body   {:message "not found"}}
             (h/respond "not found" 404))))
  (t/testing "successful exec with default message"
    (t/is (= {:status 202
              :body   {:message "Ok"}}
             (h/exec #(+ 1 2)))))
  (t/testing "successful exec with supplied message"
    (t/is (= {:status 202
              :body   {:message "Yes"}}
             (h/exec #(+ 1 2) "Yes"))))
  (t/testing "failed exec"
    (t/is (= {:status 500
              :body   {:message "Divide by zero"}}
             (h/exec #(/ 5 0))))))

(t/deftest health-check-test
  (u/with-system (fn [db queue]
                   (t/testing "passing health check"
                     (let [{:keys [status body]} (h/health-check {:db    db
                                                                  :queue queue})]
                       (t/is (= status 200))
                       (t/is (= "Yes we can! 🔨 🔨"
                                (-> body
                                    :message
                                    f/message)))))
                   (t/testing "failing health check"
                     (lch/close queue)
                     (let [{:keys [status body]} (h/health-check {:db    db
                                                                  :queue queue})]
                       (t/is (= status 500))
                       (t/is (= "Queue is unreachable"
                                (-> body
                                    :message
                                    first
                                    f/message))))))))

(defn queue-get
  [chan queue]
  (let [[metadata payload] (lb/get chan queue true)]
    {:type (:type metadata)
     :data (json/read-str (String. payload "UTF-8") :key-fn keyword)}))

(t/deftest pipeline-direct-tests
  (u/with-system (fn [db queue]
                   (t/testing "pipeline creation"
                     (h/pipeline-create {:parameters {:path {:group "dev"
                                                             :name  "test"}
                                                      :body {:image "test image"
                                                             :steps []}}
                                         :queue      queue})
                     (u/spec-assert :bob.command/pipeline-create (queue-get queue "bob.entities")))
                   (t/testing "pipeline deletion"
                     (h/pipeline-delete {:parameters {:path {:group "dev"
                                                             :name  "test"}}
                                         :db         db
                                         :queue      queue})
                     (u/spec-assert :bob.command/pipeline-delete (queue-get queue "bob.entities")))
                   (t/testing "invalid pipeline deletion with active runs"
                     (xt/await-tx
                       db
                       (xt/submit-tx
                         db
                         [[::xt/put
                           {:xt/id  :bob.pipeline.run/r-1
                            :type   :pipeline-run
                            :group  "dev"
                            :name   "test"
                            :status :running}]
                          [::xt/put
                           {:xt/id  :bob.pipeline.run/r-2
                            :type   :pipeline-run
                            :group  "dev"
                            :name   "test"
                            :status :passed}]]))
                     (let [{:keys [status body]} (h/pipeline-delete {:parameters {:path {:group "dev"
                                                                                         :name  "test"}}
                                                                     :db         db
                                                                     :queue      queue})]
                       (t/is (= 422 status))
                       (t/is (= {:error "Pipeline has active runs. Wait for them to finish or stop them."
                                 :runs  ["r-1"]}
                                (:message body)))))
                   (t/testing "pipeline start default"
                     (h/pipeline-start {:parameters {:path {:group "dev"
                                                            :name  "test"}}
                                        :queue      queue
                                        :db         db})
                     (let [msg (queue-get queue "bob.jobs")]
                       (u/spec-assert :bob.command/pipeline-start msg)
                       (t/is (= "container"
                                (-> msg
                                    :data
                                    :metadata
                                    :runner/type)))))
                   (t/testing "pipeline start with metadata"
                     (h/pipeline-start {:parameters {:path {:group "dev"
                                                            :name  "test"}
                                                     :body {:runner/type "something else"}}
                                        :queue      queue
                                        :db         db})
                     (let [msg (queue-get queue "bob.jobs")]
                       (u/spec-assert :bob.command/pipeline-start msg)
                       (t/is (= "something else"
                                (-> msg
                                    :data
                                    :metadata
                                    :runner/type)))))
                   (t/testing "starting paused pipeline"
                     (xt/await-tx
                       db
                       (xt/submit-tx
                         db
                         [[::xt/put
                           {:xt/id  :bob.pipeline.dev-paused/test-paused
                            :type   :pipeline
                            :group  "dev-paused"
                            :name   "test-paused"
                            :image  "busybox:musl"
                            :paused true
                            :steps  [{:cmd "echo yes"}]}]]))
                     (let [{:keys [status body]} (h/pipeline-start {:parameters {:path {:group "dev-paused"
                                                                                        :name  "test-paused"}}
                                                                    :queue      queue
                                                                    :db         db})]
                       (t/is (= 422 status))
                       (t/is (= "Pipeline is paused. Unpause it first." (:message body))))))))

(t/deftest pipeline-fanout-tests
  (u/with-system (fn [db queue]
                   (lq/declare queue
                               "bob.tests"
                               {:exclusive   true
                                :auto-delete true
                                :durable     false})
                   (lq/bind queue "bob.tests" "bob.fanout")
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[::xt/put
                                                {:xt/id  :bob.pipeline.run/a-run-id
                                                 :type   :pipeline-run
                                                 :group  "dev"
                                                 :name   "test"
                                                 :status :running}]]))
                   (t/testing "pipeline stop"
                     (h/pipeline-stop {:parameters {:path {:group "dev"
                                                           :name  "test"
                                                           :id    "r-a-run-id"}}
                                       :queue      queue})
                     (u/spec-assert :bob.command/pipeline-stop (queue-get queue "bob.tests"))))))

(t/deftest pipeline-pause-unpause
  (u/with-system (fn [db _]
                   (xt/await-tx
                     db
                     (xt/submit-tx
                       db
                       [[::xt/put
                         {:xt/id :bob.pipeline.dev/test
                          :type  :pipeline
                          :group "dev"
                          :name  "test"
                          :image "busybox:musl"
                          :steps [{:cmd "echo yes"}]}]]))
                   (t/testing "pipeline pause"
                     (h/pipeline-pause-unpause true
                                               {:parameters {:path {:group "dev"
                                                                    :name  "test"}}
                                                :db         db})
                     (t/is (:paused (h/pipeline-data db "dev" "test"))))
                   (t/testing "pipeline unpause"
                     (h/pipeline-pause-unpause false
                                               {:parameters {:path {:group "dev"
                                                                    :name  "test"}}
                                                :db         db})
                     (t/is (not (:paused (h/pipeline-data db "dev" "test"))))))))

(t/deftest pipeline-logs-test
  (u/with-system (fn [db _]
                   (t/testing "pipeline logs"
                     (xt/await-tx
                       db
                       (xt/submit-tx db
                                     [[::xt/put
                                       {:xt/id  :bob.pipeline.log/l-1
                                        :type   :log-line
                                        :time   (Instant/now)
                                        :run-id "r-1"
                                        :line   "l1"}]
                                      [::xt/put
                                       {:xt/id  :bob.pipeline.log/l-2
                                        :type   :log-line
                                        :time   (Instant/now)
                                        :run-id "r-1"
                                        :line   "l2"}]
                                      [::xt/put
                                       {:xt/id  :bob.pipeline.log/l-3
                                        :type   :log-line
                                        :time   (Instant/now)
                                        :run-id "r-1"
                                        :line   "l3"}]]))
                     (t/is (= ["l1" "l2"]
                              (-> (h/pipeline-logs {:db         db
                                                    :parameters {:path {:id     "r-1"
                                                                        :offset 0
                                                                        :lines  2}}})
                                  :body
                                  :message)))))))

(t/deftest pipeline-status-test
  (u/with-system (fn [db _]
                   (t/testing "existing pipeline status"
                     (xt/await-tx
                       db
                       (xt/submit-tx db
                                     [[::xt/put
                                       {:xt/id  :bob.pipeline.run/r-1
                                        :type   :pipeline-run
                                        :group  "dev"
                                        :name   "test"
                                        :status :running}]]))
                     (t/is (= :running
                              (-> (h/pipeline-status {:db         db
                                                      :parameters {:path {:id "r-1"}}})
                                  :body
                                  :message))))
                   (t/testing "non-existing pipeline status"
                     (let [{:keys [status body]}
                           (h/pipeline-status {:db         db
                                               :parameters {:path {:id "r-2"}}})]
                       (t/is (= 404 status))
                       (t/is (= "Cannot find status"
                                (:message body))))))))

(t/deftest pipeline-artifact-fetch-test
  (u/with-system (fn [db _]
                   (t/testing "fetching a valid artifact"
                     (xt/await-tx
                       db
                       (xt/submit-tx
                         db
                         [[::xt/put
                           {:xt/id :bob.artifact-store/local
                            :type  :artifact-store
                            :name  "local"
                            :url   "http://localhost:8001"}]]))
                     (http/post "http://localhost:8001/bob_artifact/dev/test/a-run-id/file"
                                {:as   :input-stream
                                 :body (io/input-stream "test/test.tar")})
                     (let [{:keys [status headers]}
                           (h/pipeline-artifact {:db         db
                                                 :parameters {:path {:group         "dev"
                                                                     :name          "test"
                                                                     :id            "a-run-id"
                                                                     :store-name    "local"
                                                                     :artifact-name "file"}}})]
                       (t/is (= 200 status))
                       (t/is (= "application/tar" (get headers "Content-Type")))))
                   (t/testing "unregistered artifact store"
                     (let [{:keys [status body]}
                           (h/pipeline-artifact {:db         db
                                                 :parameters {:path {:group         "dev"
                                                                     :name          "test"
                                                                     :id            "a-run-id"
                                                                     :store-name    "nope"
                                                                     :artifact-name "file"}}})]
                       (t/is (= 400 status))
                       (t/is (= "Cannot locate artifact store nope" (:message body)))))
                   (t/testing "non-existing artifact"
                     (let [{:keys [status]}
                           (h/pipeline-artifact {:db         db
                                                 :parameters {:path {:group         "dev"
                                                                     :name          "test"
                                                                     :id            "a-run-id"
                                                                     :store-name    "local"
                                                                     :artifact-name "yes"}}})]
                       (t/is (= 400 status)))))))

(t/deftest pipeline-list-test
  (u/with-system (fn [db _]
                   (t/testing "listing pipelines"
                     (xt/await-tx
                       db
                       (xt/submit-tx
                         db
                         [[::xt/put
                           {:xt/id :bob.pipeline.dev/test1
                            :type  :pipeline
                            :group "dev"
                            :name  "test1"
                            :image "busybox:musl"
                            :steps [{:cmd "echo yes"}]}]
                          [::xt/put
                           {:xt/id :bob.pipeline.dev/test2
                            :type  :pipeline
                            :group "dev"
                            :name  "test2"
                            :image "alpine:latest"
                            :steps [{:cmd "echo yesnt"}]}]
                          [::xt/put
                           {:xt/id :bob.pipeline.prod/test1
                            :type  :pipeline
                            :group "prod"
                            :name  "test1"
                            :image "alpine:latest"
                            :steps [{:cmd "echo boo"}]}]]))
                     (let [resp (h/pipeline-list {:db         db
                                                  :parameters {:query {:group "dev"}}})]
                       (t/is (= [{:group "dev"
                                  :image "alpine:latest"
                                  :name  "test2"
                                  :steps [{:cmd "echo yesnt"}]}
                                 {:group "dev"
                                  :image "busybox:musl"
                                  :name  "test1"
                                  :steps [{:cmd "echo yes"}]}]
                                (-> resp
                                    :body
                                    :message))))))))

(t/deftest pipeline-runs-list-test
  (u/with-system (fn [db _]
                   (t/testing "listing pipeline runs"
                     (xt/await-tx
                       db
                       (xt/submit-tx
                         db
                         [[::xt/put
                           {:xt/id  :bob.pipeline.run/r-1
                            :type   :pipeline-run
                            :group  "dev"
                            :name   "test"
                            :status :passed}]
                          [::xt/put
                           {:xt/id  :bob.pipeline.run/r-2
                            :type   :pipeline-run
                            :group  "dev"
                            :name   "test"
                            :status :failed}]]))
                     (let [resp (h/pipeline-runs-list {:db         db
                                                       :parameters {:path {:group "dev"
                                                                           :name  "test"}}})]
                       (t/is (= [{:run_id "r-1"
                                  :status :passed}
                                 {:run_id "r-2"
                                  :status :failed}]
                                (-> resp
                                    :body
                                    :message))))))))

(t/deftest resource-provider-test
  (u/with-system (fn [db queue]
                   (t/testing "resource-provider registration"
                     (h/resource-provider-create {:parameters {:path {:name "git"}
                                                               :body {:url "http://localhost:8000"}}
                                                  :queue      queue})
                     (u/spec-assert :bob.command/resource-provider-create (queue-get queue "bob.entities")))
                   (t/testing "resource-provider de-registration"
                     (h/resource-provider-delete {:parameters {:path {:name "git"}}
                                                  :queue      queue})
                     (u/spec-assert :bob.command/resource-provider-delete (queue-get queue "bob.entities")))
                   (t/testing "resource-provider listing"
                     (xt/await-tx
                       db
                       (xt/submit-tx db
                                     [[::xt/put
                                       {:xt/id :bob.resource-provider.dev/test1
                                        :type  :resource-provider
                                        :name  "test1"
                                        :url   "http://localhost:8000"}]
                                      [::xt/put
                                       {:xt/id :bob.resource-provider.dev/test2
                                        :type  :resource-provider
                                        :name  "test2"
                                        :url   "http://localhost:8001"}]]))
                     (t/is (= [{:name "test1"
                                :url  "http://localhost:8000"}
                               {:name "test2"
                                :url  "http://localhost:8001"}]
                              (-> (h/resource-provider-list {:db db})
                                  :body
                                  :message)))))))

(t/deftest artifact-store-test
  (u/with-system (fn [db queue]
                   (t/testing "artifact-store registration"
                     (h/artifact-store-create {:parameters {:path {:name "s3"}
                                                            :body {:url "http://localhost:8000"}}
                                               :queue      queue})
                     (u/spec-assert :bob.command/artifact-store-create (queue-get queue "bob.entities")))
                   (t/testing "artifact-store de-registration"
                     (h/artifact-store-delete {:parameters {:path {:name "s3"}}
                                               :queue      queue})
                     (u/spec-assert :bob.command/artifact-store-delete (queue-get queue "bob.entities")))
                   (t/testing "artifact-store listing"
                     (xt/await-tx
                       db
                       (xt/submit-tx db
                                     [[::xt/put
                                       {:xt/id :bob.resource-provider.dev/test1
                                        :type  :artifact-store
                                        :name  "test1"
                                        :url   "http://localhost:8000"}]
                                      [::xt/put
                                       {:xt/id :bob.resource-provider.dev/test2
                                        :type  :artifact-store
                                        :name  "test2"
                                        :url   "http://localhost:8001"}]]))
                     (t/is (= [{:name "test1"
                                :url  "http://localhost:8000"}
                               {:name "test2"
                                :url  "http://localhost:8001"}]
                              (-> (h/artifact-store-list {:db db})
                                  :body
                                  :message)))))))

(t/deftest raw-query-test
  (u/with-system
    (fn [db _]
      (t/testing "direct query"
        (xt/await-tx
          db
          (xt/submit-tx
            db
            [[::xt/put
              {:xt/id :food/biryani
               :type  :indian}]]))
        (t/is
          (=
            "[[{\"type\":\"indian\"}]]"
            (:body
              (h/query
                {:db         db
                 :parameters
                 {:query
                  {:q "{:find  [(pull f [:type])]
                        :where [[f :type :indian]]})"}}})))))
      (t/testing "timed query"
        (let [point-in-time (str (Instant/now))]
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
                  {:db         db
                   :parameters
                   {:query
                    {:q
                     "{:find  [(pull f [:type])]
                              :where [[f :type :indian]]})"
                     :t point-in-time}}})))))))))
