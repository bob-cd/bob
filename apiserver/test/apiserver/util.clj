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
