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
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [hugsql.core :as sql]
            [clj-docker-client.core :as docker]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [taoensso.timbre :as log]
            [bob.util :as u]
            [bob.states :as states]))

(sql/def-db-fns (io/resource "sql/health_checks.sql"))

(defn health-check
  "Check the systems we depend upon."
  []
  (d/let-flow [docker   (when (f/failed? (f/try* (docker/ping states/docker-conn))) "Docker")
               postgres (when (f/failed? (f/try* (db-health-check states/db))) "Postgres")
               failures (filter some? [docker postgres])]
              (if (empty? failures)
                (do (log/debugf "Health check succeeded") (u/respond "Yes, we can! \uD83D\uDD28 \uD83D\uDD28"))
                (let [failstr (str "Health check failed: " (string/join " and " failures) " not healthy")]
                  (log/errorf failstr)
                  (u/service-unavailable failstr)))))

(comment
  (health-check))
