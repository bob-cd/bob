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

(ns apiserver.handlers-test
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [failjure.core :as f]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [langohr.queue :as lq]
            [jsonista.core :as json]
            [crux.api :as crux]
            [java-http-clj.core :as http]
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
     :data (json/read-value payload json/keyword-keys-object-mapper)}))

(t/deftest pipeline-direct-tests
  (u/with-system (fn [_ queue]
                   (t/testing "pipeline creation"
                     (h/pipeline-create {:parameters {:path {:group "dev"
                                                             :name  "test"}
                                                      :body {:image "test image"}}
                                         :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.entities")]
                       (t/is (= {:image "test image"
                                 :group "dev"
                                 :name  "test"}
                                data))
                       (t/is (= "pipeline/create" type))))
                   (t/testing "pipeline deletion"
                     (h/pipeline-delete {:parameters {:path {:group "dev"
                                                             :name  "test"}}
                                         :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.entities")]
                       (t/is (= {:group "dev"
                                 :name  "test"}
                                data))
                       (t/is (= "pipeline/delete" type))))
                   (t/testing "pipeline start default"
                     (h/pipeline-start {:parameters {:path {:group "dev"
                                                            :name  "test"}}
                                        :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.jobs")]
                       (t/is (contains? data :run_id))
                       (t/is (= {:group    "dev"
                                 :name     "test"
                                 :metadata {:runner/type "docker"}}
                                (dissoc data :run_id)))
                       (t/is (= "pipeline/start" type))))
                   (t/testing "pipeline start with metadata"
                     (h/pipeline-start {:parameters {:path {:group "dev"
                                                            :name  "test"}
                                                     :body {:runner/type "something else"}}
                                        :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.jobs")]
                       (println data)
                       (t/is (contains? data :run_id))
                       (t/is (= {:group    "dev"
                                 :name     "test"
                                 :metadata {:runner/type "something else"}}
                                (dissoc data :run_id)))
                       (t/is (= "pipeline/start" type)))))))

(t/deftest pipeline-fanout-tests
  (u/with-system (fn [_ queue]
                   (lq/declare queue
                               "bob.tests"
                               {:exclusive   true
                                :auto-delete true
                                :durable     false})
                   (lq/bind queue "bob.tests" "bob.fanout")
                   (t/testing "pipeline stop"
                     (h/pipeline-stop {:parameters {:path {:group "dev"
                                                           :name  "test"
                                                           :id    "a-run-id"}}
                                       :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.tests")]
                       (t/is (= {:group  "dev"
                                 :name   "test"
                                 :run_id "a-run-id"}
                                data))
                       (t/is (= "pipeline/stop" type))))
                   (t/testing "pipeline pause"
                     (h/pipeline-pause-unpause true
                                               {:parameters {:path {:group "dev"
                                                                    :name  "test"
                                                                    :id    "a-run-id"}}
                                                :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.tests")]
                       (t/is (= {:group  "dev"
                                 :name   "test"
                                 :run_id "a-run-id"}
                                data))
                       (t/is (= "pipeline/pause" type))))
                   (t/testing "pipeline unpause"
                     (h/pipeline-pause-unpause false
                                               {:parameters {:path {:group "dev"
                                                                    :name  "test"
                                                                    :id    "a-run-id"}}
                                                :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.tests")]
                       (t/is (= {:group  "dev"
                                 :name   "test"
                                 :run_id "a-run-id"}
                                data))
                       (t/is (= "pipeline/unpause" type)))))))

(t/deftest pipeline-logs-test
  (u/with-system (fn [db _]
                   (t/testing "pipeline logs"
                     (crux/await-tx
                       db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.pipeline.log/l-1
                                          :type       :log-line
                                          :time       (Instant/now)
                                          :run-id     "r-1"
                                          :line       "l1"}]
                                        [:crux.tx/put
                                         {:crux.db/id :bob.pipeline.log/l-2
                                          :type       :log-line
                                          :time       (Instant/now)
                                          :run-id     "r-1"
                                          :line       "l2"}]
                                        [:crux.tx/put
                                         {:crux.db/id :bob.pipeline.log/l-3
                                          :type       :log-line
                                          :time       (Instant/now)
                                          :run-id     "r-1"
                                          :line       "l3"}]]))
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
                     (crux/await-tx
                       db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.pipeline.run/r-1
                                          :type       :pipeline-run
                                          :group      "dev"
                                          :name       "test"
                                          :status     :running}]]))
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
                     (crux/await-tx
                       db
                       (crux/submit-tx
                         db
                         [[:crux.tx/put
                           {:crux.db/id :bob.artifact-store/local
                            :type       :artifact-store
                            :url        "http://localhost:8001"}]]))
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
                     (crux/await-tx
                       db
                       (crux/submit-tx
                         db
                         [[:crux.tx/put
                           {:crux.db/id :bob.pipeline.dev/test1
                            :type       :pipeline
                            :group      "dev"
                            :name       "test1"
                            :image      "busybox:musl"
                            :steps      [{:cmd "echo yes"}]}]
                          [:crux.tx/put
                           {:crux.db/id :bob.pipeline.dev/test2
                            :type       :pipeline
                            :group      "dev"
                            :name       "test2"
                            :image      "alpine:latest"
                            :steps      [{:cmd "echo yesnt"}]}]
                          [:crux.tx/put
                           {:crux.db/id :bob.pipeline.prod/test1
                            :type       :pipeline
                            :group      "prod"
                            :name       "test1"
                            :image      "alpine:latest"
                            :steps      [{:cmd "echo boo"}]}]]))
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
                     (crux/await-tx
                       db
                       (crux/submit-tx
                         db
                         [[:crux.tx/put
                           {:crux.db/id :bob.pipeline.run/r-1
                            :type       :pipeline-run
                            :group      "dev"
                            :name       "test"
                            :status     :passed}]
                          [:crux.tx/put
                           {:crux.db/id :bob.pipeline.run/r-2
                            :type       :pipeline-run
                            :group      "dev"
                            :name       "test"
                            :status     :failed}]]))
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
                     (let [{:keys [type data]} (queue-get queue "bob.entities")]
                       (t/is (= {:name "git"
                                 :url  "http://localhost:8000"}
                                data))
                       (t/is (= "resource-provider/create" type))))
                   (t/testing "resource-provider de-registration"
                     (h/resource-provider-delete {:parameters {:path {:name "git"}}
                                                  :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.entities")]
                       (t/is (= {:name "git"} data))
                       (t/is (= "resource-provider/delete" type))))
                   (t/testing "resource-provider listing"
                     (crux/await-tx
                       db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.resource-provider.dev/test1
                                          :type       :resource-provider
                                          :name       "test1"
                                          :url        "http://localhost:8000"}]
                                        [:crux.tx/put
                                         {:crux.db/id :bob.resource-provider.dev/test2
                                          :type       :resource-provider
                                          :name       "test2"
                                          :url        "http://localhost:8001"}]]))
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
                     (let [{:keys [type data]} (queue-get queue "bob.entities")]
                       (t/is (= {:name "s3"
                                 :url  "http://localhost:8000"}
                                data))
                       (t/is (= "artifact-store/create" type))))
                   (t/testing "artifact-store de-registration"
                     (h/artifact-store-delete {:parameters {:path {:name "s3"}}
                                               :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.entities")]
                       (t/is (= {:name "s3"} data))
                       (t/is (= "artifact-store/delete" type))))
                   (t/testing "artifact-store listing"
                     (crux/await-tx
                       db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.resource-provider.dev/test1
                                          :type       :artifact-store
                                          :name       "test1"
                                          :url        "http://localhost:8000"}]
                                        [:crux.tx/put
                                         {:crux.db/id :bob.resource-provider.dev/test2
                                          :type       :artifact-store
                                          :name       "test2"
                                          :url        "http://localhost:8001"}]]))
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
        (crux/await-tx
          db
          (crux/submit-tx
            db
            [[:crux.tx/put
              {:crux.db/id :food/biryani
               :type       :indian}]]))
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
          (crux/await-tx
            db
            (crux/submit-tx
              db
              [[:crux.tx/delete :food/biryani]]))
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
