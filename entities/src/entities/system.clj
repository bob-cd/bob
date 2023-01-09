; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [common.dispatch :as d]
   [common.system :as cs]
   [entities.artifact-store :as artifact-store]
   [entities.pipeline :as pipeline]
   [entities.resource-provider :as resource-provider]
   [integrant.core :as ig]))

(def ^:private routes
  {"pipeline/create"          pipeline/create
   "pipeline/delete"          pipeline/delete
   "artifact-store/create"    artifact-store/register-artifact-store
   "artifact-store/delete"    artifact-store/un-register-artifact-store
   "resource-provider/create" resource-provider/register-resource-provider
   "resource-provider/delete" resource-provider/un-register-resource-provider})

(defmethod ig/init-key
  :entities/queue-config
  [_ {:keys [database]}]
  {:exchanges     {"bob.direct" {:type    "direct"
                                 :durable true}}
   :queues        {"bob.errors"   {:exclusive   false
                                   :auto-delete false
                                   :durable     true}
                   "bob.entities" {:exclusive   false
                                   :auto-delete false
                                   :durable     true}}
   :bindings      {"bob.entities" "bob.direct"}
   :subscriptions {"bob.entities" (partial d/queue-msg-subscriber database routes)}})

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (-> "bob/conf.edn"
                                  (io/resource)
                                  (aero/read-config {:resolver cs/resource-resolver})
                                  (dissoc :common)
                                  (ig/init)))))

(defn stop
  []
  (alter-var-root #'system
                  #(when %
                     (ig/halt! %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset))
