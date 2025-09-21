; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.healthcheck
  (:require
   [babashka.http-client :as http]
   [clojure.tools.logging :as log]
   [common.store :as store]
   [failjure.core :as f])
  (:import
   [com.rabbitmq.client Channel]
   [java.util.concurrent ExecutorService Future]))

(defn queue
  [{:keys [queue]}]
  (when (not (Channel/.isOpen queue))
    (f/fail "Queue is unreachable")))

(defn db
  [{:keys [db]}]
  (f/try* (store/ping db)))

(defn check-entity
  [{:keys [name url]}]
  (f/try-all [{status :status} (http/get (str url "/ping") {:throw false})]
    (if (>= status 400)
      (f/fail (format "Error checking %s at %s" name url))
      "Ok")
    (f/when-failed [_]
      (f/fail (format "Error checking %s at %s" name url)))))

(defn check-entities
  [{db :db}]
  (let [artifact-stores (store/get db "bob.artifact-store/" {:prefix true})
        resource-providers (store/get db "bob.resource-provider/" {:prefix true})
        loggers (store/get db "bob.logger/" {:prefix true})]
    (->> (concat artifact-stores resource-providers loggers)
         (map :value)
         (map check-entity)
         (filter f/failed?)
         (into []))))

(defn check
  [executor opts]
  (let [results (->> [queue db check-entities]
                     (map #(fn [] (% opts)))
                     (ExecutorService/.invokeAll executor)
                     (map Future/.get)
                     (flatten)
                     (filter f/failed?))]
    (when (seq results)
      (run! #(log/errorf "Health checks failing: %s" (f/message %)) results)
      (f/fail results))))

(comment
  (set! *warn-on-reflection* true)

  (->> (into (list {:name "a1" :url "https://httpbin.org/get"})
             (list {:name "r1" :url "http://localhost:8000/ping"}))
       (map check-entity)))
