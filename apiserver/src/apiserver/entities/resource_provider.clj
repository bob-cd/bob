; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.resource-provider
  (:require
   [xtdb.api :as xt]))

(defn create
  "Registers an resource-provider with an unique name and an url supplied in a map."
  [db {:keys [name url]}]
  (xt/await-tx
   db
   (xt/submit-tx db
                 [[::xt/put
                   {:xt/id (keyword (str "bob.resource-provider/" name))
                    :type :resource-provider
                    :url url
                    :name name}]])))

(defn delete
  "Unregisters an resource-provider by its name supplied in a map."
  [db id]
  (xt/await-tx
   db
   (xt/submit-tx db [[::xt/delete (keyword (str "bob.resource-provider/" id))]])))
