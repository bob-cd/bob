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
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :refer [migrate]])
  (:import (java.io File)))

(defonce db-spec
         {:classname   "org.h2.Driver"
          :subprotocol "h2:file"
          :subname     (str (System/getProperty "user.home")
                            File/separator
                            ".bob")})

(def migration-config
  {:datastore  (jdbc/sql-database db-spec)
   :migrations (jdbc/load-resources "migrations")})

(defn init-db []
  (migrate migration-config))
