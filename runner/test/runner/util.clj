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

(ns runner.util
  (:require [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [runner.system :as sys]))

(defn with-system
  [test-fn]
  (let [system (component/system-map
                 :database (sys/map->Database {:db-url      "jdbc:postgresql://localhost:5433/bob-test"
                                               :db-user     "bob"
                                               :db-password "bob"})
                 :queue    (component/using (sys/map->Queue {:queue-url      "amqp://localhost:5673"
                                                             :queue-user     "guest"
                                                             :queue-password "guest"})
                                            [:database]))
        {:keys [database queue]
         :as   com}
        (component/start system)
        ds (jdbc/get-datasource {:dbtype   "postgresql"
                                 :dbname   "bob-test"
                                 :user     "bob"
                                 :password "bob"
                                 :host     "localhost"
                                 :port     5433})]
    (test-fn (sys/db-client database)
             (sys/queue-chan queue))
    (component/stop com)
    ;; Reset DB fully for Crux
    (jdbc/execute! ds ["DELETE FROM tx_events;"])))
