; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [aero.core :as aero]
            [common.system]
            [common.dispatch :as d]
            [runner.pipeline :as p]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop"  p/stop})

(defmethod ig/init-key
  :runner/queue-config
  [_ {:keys [database]}]
  (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
        subscriber      (partial d/queue-msg-subscriber database routes)]
    {:exchanges     {"bob.direct" {:type    "direct"
                                   :durable true}
                     "bob.fanout" {:type    "fanout"
                                   :durable true}}
     :queues        {"bob.jobs"      {:exclusive   false
                                      :auto-delete false
                                      :durable     true}
                     "bob.errors"    {:exclusive   false
                                      :auto-delete false
                                      :durable     true}
                     broadcast-queue {:exclusive   true
                                      :auto-delete true
                                      :durable     true}}
     :bindings      {"bob.jobs"      "bob.direct"
                     broadcast-queue "bob.fanout"}
     :subscriptions {"bob.jobs"      subscriber
                     broadcast-queue subscriber}}))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (-> "bob/conf.edn"
                                  (io/resource)
                                  (aero/read-config)
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
