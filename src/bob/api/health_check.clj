(ns bob.api.health-check
  (:require [hugsql.core :as sql]
            [clojure.java.io :as io]
            [clj-docker-client.core :as docker]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [bob.util :as u]
            [bob.states :as states]))

(sql/def-db-fns (io/resource "sql/health_checks.sql"))

(defn docker-health-check
  [docker-conn]
  (docker/ping docker-conn))

(defn health-check
  "Check the systems we depend upon."
  []
  (d/let-flow [result (f/try-all [_ (db-health-check states/db)
                                  _ (docker-health-check states/docker-conn)]
                                 "Yes, we can! \uD83D\uDD28 \uD83D\uDD28"
                                 (f/when-failed [err] err))] ;;400 msg
              (u/respond result)))


(comment 
  @(health-check)

  (docker-health-check states/docker-conn)

  (db-health-check states/db))
