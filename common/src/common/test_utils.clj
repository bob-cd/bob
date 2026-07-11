; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.test-utils
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [common.store :as store]
   [common.system :as cs]
   [integrant.core :as ig]))

(defn clear-db
  [db]
  (doseq [bucket [store/pipeline-bucket
                  store/resource-provider-bucket
                  store/artifact-store-bucket
                  store/logger-bucket
                  store/pipeline-run-bucket
                  store/cluster-bucket]
          k (map :key (store/kv-list db bucket))]
    (store/kv-del db bucket k)))

(defn- with-system
  [test-fn]
  (let [config (-> "bob/conf.edn"
                   (io/resource)
                   (aero/read-config {:resolver cs/resource-resolver})
                   (dissoc :common)
                   (assoc-in [:bob/storage :urls] "nats://localhost:4223")
                   (assoc-in [:bob/queue :url] "amqp://localhost:5673")
                   (assoc-in [:bob/stream :url] "rabbitmq-stream://guest:guest@localhost:5552/%2f"))
        system (ig/init config)]
    (test-fn (system :bob/storage)
             (-> system
                 :bob/queue
                 :chan)
             (system :bob/stream))
    (clear-db (:bob/storage system))
    (ig/halt! system)))

(defn with-apiserver-system
  [test-fn]
  (require '[apiserver.system])
  (with-system test-fn))

(defn with-runner-system
  [test-fn]
  (require '[runner.system])
  (with-system test-fn))

(defn spec-assert
  [spec value]
  (t/is (s/valid? spec value)
        (s/explain spec value)))
