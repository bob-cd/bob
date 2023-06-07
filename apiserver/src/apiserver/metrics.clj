; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.metrics
  (:require
   [babashka.http-client :as http]
   [clojure.data.json :as json]
   [failjure.core :as f]
   [iapetos.core :as prometheus]
   [iapetos.export :as export]
   [langohr.queue :as lq]
   [xtdb.api :as xt]))

(def registry
  (-> (prometheus/collector-registry)
      (prometheus/register
       (prometheus/gauge :bob/queued-jobs {:description "Number of queued jobs"})
       (prometheus/gauge :bob/errors {:description "Number of errors"})
       (prometheus/gauge :bob/running-jobs {:description "Number of jobs currently running"})
       (prometheus/gauge :bob/initializing-jobs {:description "Number of jobs currently initializing"})
       (prometheus/gauge :bob/failed-jobs {:description "Number of failed jobs"})
       (prometheus/gauge :bob/passed-jobs {:description "Number of passed jobs"})
       (prometheus/gauge :bob/stopped-jobs {:description "Number of stopped jobs"}))))

(def status-count-map
  {:running :bob/running-jobs
   :initializing :bob/initializing-jobs
   :failed :bob/failed-jobs
   :passed :bob/passed-jobs
   :stopped :bob/stopped-jobs})

(defn count-statuses
  [db]
  (f/try-all [result (xt/q (xt/db db)
                           '{:find [(pull run [:status])]
                             :where [[run :type :pipeline-run]]})
              counts (->> result
                          (map first)
                          (map :status)
                          (frequencies))]
    (doseq [[status count] counts]
      (when-let [metric (status-count-map status)]
        (prometheus/set (registry metric) count)))
    (f/when-failed [err]
      err)))

(defn job-queues
  [{:keys [api-url username password]}]
  (let [data (-> (str api-url "/queues/%2F")
                 (http/get {:basic-auth [username password]
                            :headers {"Content-Type" "application/json"}})
                 (:body)
                 (json/read-str {:key-fn keyword}))]
    (->> data
         (map :name)
         (filter #(re-matches #"bob\..*.\.jobs" %)))))

(defn collect-metrics
  [queue db queue-conn-opts]
  (f/try-all [_ (->> (job-queues queue-conn-opts)
                     (map #(lq/message-count queue %))
                     (run! #(prometheus/set (registry :bob/queued-jobs) %)))
              _ (->> "bob.errors"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/errors)))
              _ (count-statuses db)]
    (export/text-format registry)
    (f/when-failed [err]
      err)))

(comment
  (job-queues {:api-url "http://localhost:15672" :username "guest" :password "guest"}))
