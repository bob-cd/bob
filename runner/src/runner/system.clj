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
   [common.system :as cs]
   [integrant.core :as ig]
   [runner.pipeline :as p]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop" p/stop})

(defmethod ig/init-key
  :runner/queue-config
  [_ {:keys [database]}]
  (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
        subscriber (partial d/queue-msg-subscriber database routes)
        jobs-queue "bob.container.jobs"]
    {:exchanges {"bob.direct" {:type "direct"
                               :durable true}
                 "bob.fanout" {:type "fanout"
                               :durable true}}
     :queues {jobs-queue {:exclusive false
                          :auto-delete false
                          :durable true}
              "bob.errors" {:exclusive false
                            :auto-delete false
                            :durable true}
              broadcast-queue {:exclusive true
                               :auto-delete true
                               :durable true}}
     :bindings {jobs-queue "bob.direct"
                broadcast-queue "bob.fanout"}
     :subscriptions {jobs-queue subscriber
                     broadcast-queue subscriber}}))

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
