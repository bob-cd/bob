; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.util
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [common.system :as cs]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [runner.system]))

(defn with-system
  [test-fn]
  (let [config (-> "bob/conf.edn"
                   (io/resource)
                   (aero/read-config {:resolver cs/resource-resolver})
                   (dissoc :common)
                   (assoc-in [:bob/storage :url] "jdbc:postgresql://localhost:5433/bob-test")
                   (assoc-in [:bob/queue :url] "amqp://localhost:5673"))
        ds (jdbc/get-datasource {:dbtype "postgresql"
                                 :dbname "bob-test"
                                 :user "bob"
                                 :password "bob"
                                 :host "localhost"
                                 :port 5433})
        system (ig/init config)]
    (test-fn (system :bob/storage)
             (-> system
                 :bob/queue
                 :chan))
    (ig/halt! system)
    (jdbc/execute! ds ["DELETE FROM tx_events;"])))

(defn spec-assert
  [spec value]
  (t/is (s/valid? spec value)
        (s/explain spec value)))
