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
            [crux.api :as crux]
            [entities.errors :as err]))

(defn register-artifact-store
  "Registers an artifact store with an unique name and an url supplied in a map."
  [db-client queue-chan data]
  (let [result (f/try*
                 (crux/submit-tx db-client
                                 [[:crux.tx/put
                                   {:crux.db/id (keyword (str "bob.artifact-store/" (:name data)))
                                    :type       :artifact-store
                                    :url        (:url data)}]]))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Could not register artifact store: %s" (f/message result)))
      (do
        (log/infof "Registered artifact store at: %s" data)
        "Ok"))))

(defn un-register-artifact-store
  "Unregisters an artifact store by its name supplied in a map."
  [db-client _queue-chan data]
  (f/try*
    (crux/submit-tx db-client [[:crux.tx/delete (keyword (str "bob.artifact-store/" (:name data)))]]))
  (log/infof "Un-registered artifact store %s" (:name data))
  "Ok")

(comment
  (keyword (str "bob.artifact-store/" "test"))

  (let [client (crux/new-api-client "http://localhost:7778")]
    (register-artifact-store client
                             nil
                             {:name "local"
                              :url  "http://localhost:8002"})
    (.close client))

  (let [client (crux/new-api-client "http://localhost:7778")]
    (un-register-artifact-store client
                                nil
                                {:name "local"})
    (.close client)))
