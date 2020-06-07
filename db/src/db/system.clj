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

(ns db.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :as env]
            [crux.api :as crux]
            [taoensso.timbre :as log]))

(defn int-from-env
  [key default]
  (try
    (Integer/parseInt (get env/env key (str default)))
    (catch Exception _ default)))

(defonce storage-host (:bob-storage-host env/env "localhost"))
(defonce storage-port (int-from-env :bob-storage-port 5432))
(defonce storage-user (:bob-storage-user env/env "bob"))
(defonce storage-name (:bob-storage-database env/env "bob"))
(defonce storage-password (:bob-storage-password env/env "bob"))
(defonce http-port (int-from-env :bob-db-port 7778))

(defprotocol IStorage
  (storage-connection [this]))

(defrecord Storage
  [db-name host port user password http-port]
  component/Lifecycle
  (start [this]
    (let [node (crux/start-node {:crux.node/topology          '[crux.jdbc/topology crux.http-server/module]
                                 :crux.jdbc/dbtype            "postgresql"
                                 :crux.jdbc/dbname            db-name
                                 :crux.jdbc/host              host
                                 :crux.jdbc/port              port
                                 :crux.jdbc/user              user
                                 :crux.jdbc/password          password
                                 :crux.http-server/port       http-port
                                 :crux.http-server/read-only? false})]
      (assoc this :node node)))
  (stop [this]
    (log/info "Disconnecting Storage")
    (.close (:node this))
    (assoc this :node nil))
  IStorage
  (storage-connection [this] (:node this)))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (fn [_]
                    (component/start
                      (Storage. storage-name storage-host storage-port storage-user storage-password http-port)))))

(defn stop
  []
  (alter-var-root #'system
                  #(when %
                     (component/stop %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset)

  (crux/submit-tx (storage-connection system)
                  [[:crux.tx/put
                    {:crux.db/id :dbpedia.resource/Pablo-Picasso
                     :name       "Pablo"
                     :last-name  "Picasso"} #inst "2018-05-18T09:20:27.966-00:00"]])

  (crux/q (crux/db (storage-connection system))
          '{:find  [e]
            :where [[e :name "Pablo"]]})

  (crux/entity (crux/db (storage-connection system)) :dbpedia.resource/Pablo-Picasso))
