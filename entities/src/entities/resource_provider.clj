; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.resource-provider
  (:require [xtdb.api :as xt]
            [entities.db :as db]))

(defn register-resource-provider
  "Registers an resource-provider with an unique name and an url supplied in a map."
  [db-client queue-chan data]
  (db/validate-and-transact db-client
                            queue-chan
                            :bob.command.resource-provider-create/data
                            data
                            [[::xt/put
                              {:xt/id (keyword (str "bob.resource-provider/" (:name data)))
                               :type  :resource-provider
                               :url   (:url data)
                               :name  (:name data)}]]
                            "resource-provider"))

(defn un-register-resource-provider
  "Unregisters an resource-provider by its name supplied in a map."
  [db-client _queue-chan data]
  (db/validate-and-transact db-client
                            nil
                            :bob.command.resource-provider-delete/data
                            data
                            [[::xt/delete (keyword (str "bob.resource-provider/" (:name data)))]]
                            "resource-provider"))
