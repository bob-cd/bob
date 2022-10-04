; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.util
  (:require
   [aero.core :as aero]
   [apiserver.system]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [common.system]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]))

(def queue-conf
  (-> "bob/conf.edn"
      (io/resource)
      (aero/read-config)
      (get-in [:bob/queue :conf])))

(defn with-system
  [test-fn]
  (let [config {:bob/storage {:url      "jdbc:postgresql://localhost:5433/bob-test"
                              :user     "bob"
                              :password "bob"}
                :bob/queue   {:conf     (-> queue-conf
                                            (update :queues
                                                    assoc
                                                    "bob.container.jobs"
                                                    {:exclusive   false
                                                     :auto-delete false
                                                     :durable     true})
                                            (update :bindings
                                                    assoc
                                                    "bob.container.jobs"
                                                    "bob.direct"))
                              :url      "amqp://localhost:5673"
                              :user     "guest"
                              :password "guest"}}
        ds     (jdbc/get-datasource {:dbtype   "postgresql"
                                     :dbname   "bob-test"
                                     :user     "bob"
                                     :password "bob"
                                     :host     "localhost"
                                     :port     5433})
        system (ig/init config)]
    (test-fn (system :bob/storage)
             (-> system
                 :bob/queue
                 :chan))
    (ig/halt! system)
    (jdbc/execute! ds ["DELETE FROM tx_events;"])))

(defn spec-assert
  [spec value]
  (t/is (s/valid? spec value)
        (s/explain spec value)))
