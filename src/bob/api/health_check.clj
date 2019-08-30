;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

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
  []
  (docker/ping states/docker-conn))

(defn pg-health-check
  []
  (db-health-check states/db))

(defn health-check
  "Check the systems we depend upon."
  []
  (d/let-flow [result (f/try-all [_ (docker-health-check)
                                  _ (pg-health-check)]
                                 (u/respond "Yes, we can! \uD83D\uDD28 \uD83D\uDD28")
                                 (f/when-failed [err] (u/service-unavailable "Docker or Postgres unavailable")))]
              result))

(comment
  (health-check)

  (docker-health-check)

  (pg-health-check))
