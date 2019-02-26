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

(ns bob.db.core
  (:require [clojure.string :as str]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]
            [korma.db :as kdb]
            [korma.core :as k]
            [hikari-cp.core :as h])
  (:import (java.io File)))

(def db-uri (format "jdbc:h2:file://%s"
                    (str (System/getProperty "user.home")
                         File/separator
                         ".bob")))

(def data-source
  (when-not *compile-files*
    (h/make-datasource {:adapter "h2"
                        :url     db-uri})))

(def migration-config
  (when-not *compile-files*
    {:datastore  (jdbc/sql-database {:connection-uri db-uri})
     :migrations (jdbc/load-resources "migrations")}))

(defn init-db
  "Initiates the data store for Bob by running all migrations
  stored in resources."
  []
  (repl/migrate migration-config))

(kdb/defdb _ {:datasource data-source
              :naming     {:keys   str/lower-case
                           :fields str/upper-case}})

(k/defentity steps)

(k/defentity logs)

(k/defentity runs
  (k/has-many logs))

(k/defentity evars)

(k/defentity artifacts)

(k/defentity resources)

(k/defentity resource-params
  (k/table :resource_params))

(k/defentity pipelines
  (k/has-many resources)
  (k/has-many resource-params)
  (k/has-many evars)
  (k/has-many steps)
  (k/has-many runs)
  (k/has-many artifacts))

(k/defentity external-resources
  (k/table :external_resources))

(comment
  (init-db))
