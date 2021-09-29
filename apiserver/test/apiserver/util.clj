; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.util
  (:require [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [common.system :as sys]
            [apiserver.system :as asys]))

(defn with-system
  [test-fn]
  (let [db    (component/start (sys/db "jdbc:postgresql://localhost:5433/bob-test" "bob" "bob"))
        queue (component/start (sys/queue "amqp://localhost:5673" "guest" "guest" asys/queue-conf))
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
