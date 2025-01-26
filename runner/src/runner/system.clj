; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [common.dispatch :as d]
   [common.heartbeat :as hb]
   [common.system :as cs]
   [integrant.core :as ig]
   [runner.pipeline :as p])
  (:import
   [java.util.concurrent Future]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop" p/stop})

(def config
  (-> "bob/conf.edn"
      (io/resource)
      (aero/read-config {:resolver cs/resource-resolver})
      (dissoc :common)))

(defmethod ig/init-key
  :runner/queue-config
  [_ {:keys [queue node-id] :as config}]
  (let [subscriber (partial d/queue-msg-subscriber config routes)
        jobs-queue (str "bob.jobs." node-id)]
    (merge-with merge
                queue
                {:queues {jobs-queue {:args {"x-dead-letter-exchange" "bob.dlx"
                                             "x-dead-letter-routing-key" "bob.dlq"
                                             "x-expires" 300000 ;; Configurable?
                                             "x-queue-type" "classic"}
                                      :props {:auto-delete true ;; TODO: should this queue be deleted on shutdown/exclusive?
                                              :durable false}}}
                 :bindings {jobs-queue "bob.direct"}
                 :subscriptions {jobs-queue subscriber}})))

(defmethod ig/init-key
  :runner/heartbeat
  [_ {:keys [queue db freq node-id]}]
  (hb/schedule #(hb/beat-it db queue freq node-id
                            :bob/runs (-> p/node-state
                                          deref
                                          :runs
                                          keys
                                          (or [])))
               "heartbeat"
               freq))

(defmethod ig/halt-key!
  :runner/heartbeat
  [_ task]
  (Future/.cancel task true))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (do (ig/init config)
                                  (log/info "Ready")))))

(defn stop
  []
  (alter-var-root #'system #(when % (ig/halt! %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (set! *warn-on-reflection* true)

  (reset))
