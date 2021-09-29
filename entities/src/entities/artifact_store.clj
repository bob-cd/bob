; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.artifact-store
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [xtdb.api :as xt]
            [common.errors :as err]))

(defn register-artifact-store
  "Registers an artifact store with an unique name and an url supplied in a map."
  [db-client queue-chan data]
  (let [result (f/try*
                 (xt/submit-tx db-client
                               [[::xt/put
                                 {:xt/id (keyword (str "bob.artifact-store/" (:name data)))
                                  :type  :artifact-store
                                  :url   (:url data)
                                  :name  (:name data)}]]))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Could not register artifact store: %s" (f/message result)))
      (do
        (log/infof "Registered artifact store at: %s" data)
        "Ok"))))

(defn un-register-artifact-store
  "Unregisters an artifact store by its name supplied in a map."
  [db-client _queue-chan data]
  (f/try*
    (xt/submit-tx db-client [[::xt/delete (keyword (str "bob.artifact-store/" (:name data)))]]))
  (log/infof "Un-registered artifact store %s" (:name data))
  "Ok")
