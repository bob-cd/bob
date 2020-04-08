;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.config
  (:require [clojure.edn :as edn]
            [environ.core :as env :refer [env]]
            [schema.core :as s]
            [taoensso.timbre :as log]
            [bob.api.schemas :as schema]))

(defn int-from-env
  [key default]
  (try
    (Integer/parseInt (get env key (str default)))
    (catch Exception _ default)))

(defn read-config-file
  "read config from .bob.conf"
  []
  (let [path (str (System/getProperty "user.home") (java.io.File/separator) ".bob.conf")]
    (try
      (when-let [conf (clojure.java.io/file path)]
        (when (.exists conf)
          (edn/read-string (slurp conf))))
      (catch Exception e
        (log/warnf "Failed to parse %s: %s" path e)))))

(defn merge-configs
  "Merging two configs"
  [confa confb]
  (try
    (merge-with merge confa confb)
    (catch Exception e
      (log/errorf "There was a problem with the format of your configuration file: %s" e)
      confa)))

(defn load-config
  "Loading the config from Java System Properties and Environment
   and merging them with .bob.conf"
  []
  (let [config-from-env  {:server   {:port           (int-from-env :bob-server-port 7777)}
                          :docker   {:uri            (:bob-docker-uri env "unix:///var/run/docker.sock")
                                     :timeouts       {:connect-timeout (int-from-env :bob-docker-connect-timeout 1000)
                                                      :read-timeout    (int-from-env :bob-docker-read-timeout 30000)
                                                      :write-timeout   (int-from-env :bob-docker-write-timeout 30000)
                                                      :call-timeout    (int-from-env :bob-docker-call-timeout 40000)}}
                          :postgres {:host           (:bob-postgres-host env "localhost")
                                     :port           (int-from-env :bob-postgres-port 5432)
                                     :user           (:bob-postgres-user env "bob")
                                     :database       (:bob-postgres-database env "bob")}}
        config-from-file (read-config-file)
        merged-config    (merge-configs config-from-env config-from-file)]
    (do (s/validate schema/Config merged-config)
        (log/debugf "Config loaded: %s" merged-config)
        merged-config)))

(defonce
  ^{:doc "Variable holding the current configuration."}
  config (load-config))

(comment
  (load-config)
  (alter-var-root #'config (fn [_] (load-config)))
  (read-config-file)
  (merge-configs {:server {:port 7777} :docker {:uri "unix:///var/run/docker.sock", :timeouts {:connect-timeout 1000, :read-timeout 30000, :write-timeout 30000, :call-timeout 40000}}, :postgres {:host "localhost", :port 5432, :user "bob", :database "bob", :ssl nil, :sslfactory nil}} (read-config-file))
  (get-in (load-config) [:docker :uri]))
