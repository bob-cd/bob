; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.artifact-store
  (:require [xtdb.api :as xt]
            [entities.db :as db]))

(defn register-artifact-store
  "Registers an artifact store with an unique name and an url supplied in a map."
  [db-client queue-chan data]
  (db/validate-and-transact db-client
                            queue-chan
                            :bob.command.artifact-store-create/data
                            data
                            [[::xt/put
                              {:xt/id (keyword (str "bob.artifact-store/" (:name data)))
                               :type  :artifact-store
                               :url   (:url data)
                               :name  (:name data)}]]
                            "artifact-store"))

(defn un-register-artifact-store
  "Unregisters an artifact store by its name supplied in a map."
  [db-client _queue-chan data]
  (db/validate-and-transact db-client
                            nil
                            :bob.command.artifact-store-delete/data
                            data
                            [[::xt/delete (keyword (str "bob.artifact-store/" (:name data)))]]
                            "artifact-store"))
