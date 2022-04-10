; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.system
  (:require [integrant.core :as ig]
            [environ.core :as env]
            [common.system :as sys]
            [common.dispatch :as d]
            [runner.pipeline :as p]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop"  p/stop})

(defonce storage-url (:bob-storage-url env/env "jdbc:postgresql://localhost:5432/bob"))
(defonce storage-user (:bob-storage-user env/env "bob"))
(defonce storage-password (:bob-storage-password env/env "bob"))

(defonce queue-url (:bob-queue-url env/env "amqp://localhost:5672"))
(defonce queue-user (:bob-queue-user env/env "guest"))
(defonce queue-password (:bob-queue-password env/env "guest"))

(def config
  (merge
    (sys/configure
      {:storage {:url      storage-url
                 :user     storage-user
                 :password storage-password}
       :queue   {:url      queue-url
                 :user     queue-user
                 :password queue-password
                 :conf     (ig/ref :runner/queue-config)}})
    {:runner/queue-config {:database (ig/ref :bob/storage)}}))

(defmethod ig/init-key
  :runner/queue-config
  [_ {:keys [database]}]
  (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
        subscriber      (partial d/queue-msg-subscriber database routes)
        conf            {:exchanges     {"bob.direct" {:type    "direct"
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
                                         broadcast-queue subscriber}}]
    conf))

(defmethod ig/halt-key!
  :runner/queue-config
  [_ _])

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (ig/init config))))

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
