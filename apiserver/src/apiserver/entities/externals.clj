; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.externals
  (:require
   [clojure.tools.logging :as log]
   [xtdb.api :as xt]))

(defn create
  "Register with an unique name and an url supplied in a map."
  [db kind {:keys [name url]}]
  (let [id (keyword (format "bob.%s/%s" kind name))]
    (log/infof "Creating %s at %s with id %s" kind url id)
    (xt/await-tx
     db
     (xt/submit-tx db
                   [[::xt/put
                     {:xt/id id
                      :type (keyword kind)
                      :url url
                      :name name}]]))))

(defn delete
  "Unregisters by its name supplied in a map."
  [db kind id]
  (log/infof "Deleting %s %s" kind id)
  (xt/await-tx
   db
   (xt/submit-tx db [[::xt/delete (keyword (format "bob.%s/%s" kind id))]])))
