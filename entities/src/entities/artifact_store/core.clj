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

(ns entities.artifact-store.core
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [entities.artifact-store.db :as db]
            [entities.errors :as err]))

(defn register-artifact-store
  "Registers an artifact store with an unique name and an url supplied in a map."
  [db-conn queue-chan data]
  (let [result (f/try* (db/register-artifact-store db-conn data))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Could not register artifact store: %s" (f/message result)))
      (do
        (log/infof "Registered artifact store at: %s" data)
        "Ok"))))

(defn un-register-artifact-store
  "Unregisters an artifact store by its name supplied in a map."
  [db-conn _queue-chan data]
  (f/try* (db/un-register-artifact-store db-conn data))
  (log/infof "Un-registered artifact store %s" name)
  "Ok")
