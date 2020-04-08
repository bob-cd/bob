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

(ns bob.states
  (:require [mount.core :as m]
            [ragtime.repl :as repl]
            [ragtime.jdbc :as jdbc]
            [hikari-cp.core :as h]
            [clj-docker-client.core :as docker]
            [environ.core :as env]
            [taoensso.timbre :as log]
            [bob.config :refer [config]]))

(defonce conn (:docker config))
(defonce postgres (:postgres config))

(defonce images (docker/client {:category :images :conn conn}))
(defonce containers (docker/client {:category :containers :conn conn}))
(defonce commit (docker/client {:category :commit :conn conn}))

(m/defstate data-source
  :start (let [data-source (h/make-datasource {:adapter            "postgresql"
                                               :username           (:user postgres)
                                               :database-name      (:database postgres)
                                               :server-name        (:host postgres)
                                               :port-number        (:port postgres)
                                               :connection-timeout 5000})]
           (defonce db {:datasource data-source})
           data-source)
  :stop  (do (log/info "Stopping DB")
             (h/close-datasource data-source)))

(m/defstate migration-config
  :start {:datastore  (jdbc/sql-database {:connection-uri (format "jdbc:postgresql://%s:%d/%s?user=%s"
                                                                  (:host postgres)
                                                                  (:port postgres)
                                                                  (:database postgres)
                                                                  (:user postgres))})
          :migrations (jdbc/load-resources "migrations")})

(m/defstate database
  :start (repl/migrate migration-config))

(comment
  (m/start)

  (m/stop)

  (get env/env :java-home "No Java?!"))
