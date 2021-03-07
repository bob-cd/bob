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

(ns apiserver_next.handlers-test
  (:require [clojure.test :as t]
            [failjure.core :as f]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [langohr.queue :as lq]
            [jsonista.core :as json]
            [crux.api :as crux]
            [apiserver_next.handlers :as h]
            [apiserver_next.util :as u])
  (:import [java.time Instant]))

(t/deftest helpers-test
  (t/testing "default response"
    (t/is (= {:status 200
              :body   {:message "response"}}
             (h/respond "response"))))
  (t/testing "response with status"
    (t/is (= {:status 404
              :body   {:message "not found"}}
             (h/respond "not found" 404))))
  (t/testing "successful exec with default message"
    (t/is (= {:status 200
              :body   {:message "Ok"}}
             (h/exec #(+ 1 2)))))
  (t/testing "successful exec with supplied message"
    (t/is (= {:status 200
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
                   (t/testing "pipeline start"
                     (h/pipeline-start {:parameters {:path {:group "dev"
                                                            :name  "test"}}
                                        :queue      queue})
                     (let [{:keys [type data]} (queue-get queue "bob.jobs")]
                       (t/is (contains? data :run_id))
                       (t/is (= {:group "dev"
                                 :name  "test"}
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
