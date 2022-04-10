; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.util
  (:require [clojure.test :as t]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [common.system :as sys]
            [runner.system]))

(defn with-system
  [test-fn]
  (let [config (sys/configure {:storage {:url      "jdbc:postgresql://localhost:5433/bob-test"
                                         :user     "bob"
                                         :password "bob"}
                               :queue   {:conf     (ig/ref :runner/queue-config)
                                         :url      "amqp://localhost:5673"
                                         :user     "guest"
                                         :password "guest"}})
        merged (merge config {:runner/queue-config {:database (ig/ref :bob/storage)}})
        ds     (jdbc/get-datasource {:dbtype   "postgresql"
                                     :dbname   "bob-test"
                                     :user     "bob"
                                     :password "bob"
                                     :host     "localhost"
                                     :port     5433})
        system (ig/init merged)]
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
