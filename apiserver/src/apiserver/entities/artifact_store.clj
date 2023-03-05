; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.artifact-store
  (:require
   [xtdb.api :as xt]))

(defn create
  "Registers an artifact store with an unique name and an url supplied in a map."
  [db {:keys [name url]}]
  (xt/await-tx
   db
   (xt/submit-tx db
                 [[::xt/put
                   {:xt/id (keyword (str "bob.artifact-store/" name))
                    :type :artifact-store
                    :url url
                    :name name}]])))

(defn delete
  "Unregisters an artifact store by its name supplied in a map."
  [db id]
  (xt/await-tx
   db
   (xt/submit-tx db [[::xt/delete (keyword (str "bob.artifact-store/" id))]])))
