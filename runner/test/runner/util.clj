; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.util
  (:require [clojure.test :as t]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [runner.system :as rsys])
  (:import [common.system Database Queue]))

(def system-map
  (component/system-map
    :database (Database. "jdbc:postgresql://localhost:5433/bob-test" "bob" "bob")
    :conf     (component/using (rsys/map->QueueConf {})
                               [:database])
    :queue    (component/using (Queue. nil "amqp://localhost:5673" "guest" "guest")
                               [:conf])))

(defn with-system
  [test-fn]
  (let [system (component/start system-map)
        ds     (jdbc/get-datasource {:dbtype   "postgresql"
                                     :dbname   "bob-test"
                                     :user     "bob"
                                     :password "bob"
                                     :host     "localhost"
                                     :port     5433})]
    (test-fn (-> system
                 :database
                 :client)
             (-> system
                 :queue
                 :chan))
    (component/stop system)
    (jdbc/execute! ds ["DELETE FROM tx_events;"])))

(defn spec-assert
  [spec value]
  (t/is (s/valid? spec value)
        (s/explain spec value)))
