; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.entities.externals
  (:require
   [clojure.tools.logging :as log]
   [common.events :as ev]
   [xtdb.api :as xt]))

(def as-id
  {"ResourceProvider" "resource-provider"
   "ArtifactStore" "artifact-store"
   "Logger" "logger"})

(defn create
  "Register with an unique name and an url supplied in a map."
  [db producer kind {:keys [name url]}]
  (let [id (keyword (str "bob." (as-id kind)) name)]
    (log/infof "Creating %s at %s with id %s" kind url id)
    (xt/await-tx
     db
     (xt/submit-tx db
                   [[::xt/put
                     {:xt/id id
                      :type (keyword (as-id kind))
                      :url url
                      :name name}]]))
    (ev/emit producer
             {:type "Normal"
              :kind kind
              :reason (str kind "Create")
              :message (str kind " " name " created/updated")})))

(defn delete
  "Unregisters by its name supplied in a map."
  [db producer kind name]
  (log/infof "Deleting %s %s" kind name)
  (xt/await-tx
   db
   (xt/submit-tx db [[::xt/delete (keyword (str "bob." (as-id kind)) name)]]))
  (ev/emit producer
           {:type "Normal"
            :kind kind
            :reason (str kind "Delete")
            :message (str kind " " name " deleted")}))
