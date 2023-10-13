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
   [langohr.queue :as lq]
   [prometheus.core :as prom]
   [xtdb.api :as xt]))

(def registry (prom/new-registry))

(def status-count-map
  {:running :bob_running_jobs
   :initializing :bob_initializing_jobs
   :failed :bob_failed_jobs
   :passed :bob_passed_jobs
   :stopped :bob_stopped_jobs})

(defn count-statuses
  [db]
  (f/try-all [result (xt/q (xt/db db)
                           '{:find [(pull run [:status :xt/id])]
                             :where [[run :type :pipeline-run]]})
              counts (->> result
                          (map first)
                          (map :status)
                          (frequencies))]
    (doseq [[status count] counts]
      (when-let [metric (status-count-map status)]
        (prom/gauge registry metric {} count)))
    (f/when-failed [err] err)))

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
                     (run! #(prom/gauge registry :bob_queued_jobs {} %)))
              _ (->> "bob.errors"
                     (lq/message-count queue)
                     (prom/gauge registry :bob_errors {}))
              _ (count-statuses db)]
    (prom/serialize registry)
    (f/when-failed [err] err)))

(comment
  (job-queues {:api-url "http://localhost:15672" :username "guest" :password "guest"}))
