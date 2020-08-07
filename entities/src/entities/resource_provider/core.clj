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

(ns entities.resource-provider.core
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [crux.api :as crux]
            [entities.errors :as err]))

(defn register-resource-provider
  "Registers a rersource provider with an unique name and an url supplied in a map."
  [db-client queue-chan data]
  (let [result (f/try*
                 (crux/submit-tx db-client
                                 [[:crux.tx/put
                                   {:crux.db/id (keyword (str "bob.resource-provider/" (:name data)))
                                    :type       :resource-provider
                                    :url        (:url data)}]]))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Could not register resource provider: %s" (f/message result)))
      (do
        (log/infof "Registered resource provider at: %s" data)
        "Ok"))))

(defn un-register-resource-provider
  "Unregisters an resource provider by its name supplied in a map."
  [db-client _queue-chan data]
  (f/try*
    (crux/submit-tx db-client [[:crux.tx/delete (keyword (str "bob.resource-provider/" (:name data)))]]))
  (log/infof "Un-registered resource provider %s" (:name data))
  "Ok")

(comment
  (let [client (crux/new-api-client "http://localhost:7778")]
    (register-resource-provider client
                                nil
                                {:name "local"
                                 :url  "http://localhost:8002"})
    (.close client))

  (let [client (crux/new-api-client "http://localhost:7778")]
    (un-register-resource-provider client
                                   nil
                                   {:name "local"})
    (.close client)))
