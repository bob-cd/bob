; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.resource-provider
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [xtdb.api :as xt]
            [common.errors :as err]))

(defn register-resource-provider
  "Registers a rersource provider with an unique name and an url supplied in a map."
  [db-client queue-chan data]
  (let [result (f/try*
                 (xt/submit-tx db-client
                               [[::xt/put
                                 {:xt/id (keyword (str "bob.resource-provider/" (:name data)))
                                  :type  :resource-provider
                                  :name  (:name data)
                                  :url   (:url data)}]]))]
    (if (f/failed? result)
      (err/publish-error queue-chan (format "Could not register resource provider: %s" (f/message result)))
      (do
        (log/infof "Registered resource provider at: %s" data)
        "Ok"))))

(defn un-register-resource-provider
  "Unregisters an resource provider by its name supplied in a map."
  [db-client _queue-chan data]
  (f/try*
    (xt/submit-tx db-client [[::xt/delete (keyword (str "bob.resource-provider/" (:name data)))]]))
  (log/infof "Un-registered resource provider %s" (:name data))
  "Ok")
