; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.util
  (:require [clojure.test :as t]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [common.system :as sys]
            [apiserver.system :as asys]))

(defn with-system
  [test-fn]
  (let [config (sys/configure {:storage {:url      "jdbc:postgresql://localhost:5433/bob-test"
                                         :user     "bob"
                                         :password "bob"}
                               :queue   {:conf     asys/queue-conf
                                         :url      "amqp://localhost:5673"
                                         :user     "guest"
                                         :password "guest"}})
        ds     (jdbc/get-datasource {:dbtype   "postgresql"
                                     :dbname   "bob-test"
                                     :user     "bob"
                                     :password "bob"
                                     :host     "localhost"
                                     :port     5433})
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
