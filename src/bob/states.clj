;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.states
  (:require [mount.core :as m]
            [ragtime.repl :as repl]
            [korma.db :as kdb]
            [clojure.string :as str]
            [ragtime.jdbc :as jdbc]
            [hikari-cp.core :as h]
            [clj-docker-client.core :as docker])
  (:import (java.io File)))

(def db-uri (format "jdbc:h2:file://%s"
                    (str (System/getProperty "user.home")
                         File/separator
                         ".bob")))

(m/defstate data-source
  :start (h/make-datasource {:adapter "h2"
                             :url     db-uri})
  :stop  (do (println "Stopping DB...")
             (h/close-datasource data-source)))

(m/defstate migration-config
  :start {:datastore  (jdbc/sql-database {:connection-uri db-uri})
          :migrations (jdbc/load-resources "migrations")})

(m/defstate database
  :start (do (repl/migrate migration-config)
             (kdb/defdb _ {:datasource data-source
                           :naming     {:keys   str/lower-case
                                        :fields str/upper-case}})))
(m/defstate docker-conn
  :start (docker/connect)
  :stop  (do (println "Closing docker connection...")
             (docker/disconnect docker-conn)))
