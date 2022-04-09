; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.util
  (:require [clojure.test :as t]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [common.system :as sys]
            [entities.system :as esys])
  (:import [common.system Database Queue]))

(defn with-system
  [test-fn]
  (let [db    (component/start (Database. "jdbc:postgresql://localhost:5433/bob-test" "bob" "bob"))
        queue (component/start (Queue. (esys/queue-conf db) "amqp://localhost:5673" "guest" "guest"))
        ds    (jdbc/get-datasource {:dbtype   "postgresql"
                                    :dbname   "bob-test"
                                    :user     "bob"
                                    :password "bob"
                                    :host     "localhost"
                                    :port     5433})]
    (test-fn (sys/db-client db) (sys/queue-chan queue))
    (component/stop queue)
    (component/stop db)
    (jdbc/execute! ds ["DELETE FROM tx_events;"])))

(defn spec-assert
  [spec value]
  (t/is (s/valid? spec value)
        (s/explain spec value)))
