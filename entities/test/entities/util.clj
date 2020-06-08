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

(ns entities.util
  (:require [com.stuartsierra.component :as component]
            [entities.system :as sys])
  (:import [entities.system Database]))

(defn with-system
  [test-fn]
  (let [url "http://localhost:7779"
        system (component/system-map
                 :database (Database. url)
                 :queue    (component/using (sys/map->Queue {:queue-host     "localhost"
                                                             :queue-port     5673
                                                             :queue-user     "guest"
                                                             :queue-password "guest"})
                                            [:database]))
        {:keys [database queue]
         :as   com}
        (component/start system)]
    (test-fn (sys/db-client database)
             (sys/queue-chan queue))
    (component/stop com)))
