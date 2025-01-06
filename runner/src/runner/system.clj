; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
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

(defonce node-id (delay (random-uuid)))

(def config
  (-> "bob/conf.edn"
      (io/resource)
      (aero/read-config {:resolver cs/resource-resolver})
      (dissoc :common)))

(defmethod ig/init-key
  :runner/queue-config
  [_ {:keys [queue] :as config}]
  (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
        subscriber (partial d/queue-msg-subscriber config routes)
        jobs-queue "bob.container.jobs"
        dlx "bob.container.dlx"
        dlq "bob.container.dlq"]
    (merge-with merge
                queue
                {:exchanges {dlx {:type "direct"
                                  :durable true}}
                 :queues {jobs-queue {:args {"x-dead-letter-exchange" dlx
                                             "x-dead-letter-routing-key" dlq}}
                          broadcast-queue {:args {"x-queue-type" "classic"}
                                           :props {:exclusive true
                                                   :auto-delete true}}
                          dlq {}}
                 :bindings {jobs-queue "bob.direct"
                            broadcast-queue "bob.fanout"
                            dlq dlx}
                 :subscriptions {jobs-queue subscriber
                                 broadcast-queue subscriber
                                 dlq (partial p/retry config)}})))

(defmethod ig/init-key
  :runner/heartbeat
  [_ {:keys [queue db freq]}]
  (hb/schedule #(hb/beat-it db queue freq @node-id
                            :bob/node-type :runner/container
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
                  (constantly (ig/init config))))

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
