; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.util
  (:require
   [aero.core :as aero]
   [apiserver.system]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [common.store :as store]
   [common.system :as cs]
   [integrant.core :as ig]))

(defn clear-db
  [db]
  (->> (store/get db "bob." {:prefix true})
       (map :key)
       (run! #(store/delete db %))))

(defn with-system
  [test-fn]
  (let [config (-> "bob/conf.edn"
                   (io/resource)
                   (aero/read-config {:resolver cs/resource-resolver})
                   (dissoc :common)
                   (assoc-in [:bob/storage :urls] "http://localhost:2380")
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

(defn spec-assert
  [spec value]
  (t/is (s/valid? spec value)
        (s/explain spec value)))
