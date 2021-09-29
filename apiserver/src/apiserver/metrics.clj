; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.metrics
  (:require [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [failjure.core :as f]
            [xtdb.api :as xt]
            [langohr.queue :as lq]))

(defonce registry
         (-> (prometheus/collector-registry)
             (prometheus/register
               (prometheus/gauge :bob/queued-entities {:description "Number of queued entity changes to be applied"})
               (prometheus/gauge :bob/queued-jobs {:description "Number of queued jobs to be picked up"})
               (prometheus/gauge :bob/errors {:description "Number of errors"})
               (prometheus/gauge :bob/running-jobs {:description "Number of jobs currently running"})
               (prometheus/gauge :bob/failed-jobs {:description "Number of failed jobs"})
               (prometheus/gauge :bob/passed-jobs {:description "Number of passed jobs"})
               (prometheus/gauge :bob/stopped-jobs {:description "Number of stopped jobs"}))))

(defn count-statuses
  [db]
  (f/try-all [statuses [:running :passed :failed :stopped]
              counts   (pmap (fn [status]
                               {:status status
                                :count  (count (xt/q (xt/db db)
                                                     `{:find  [run]
                                                       :where [[run :type :pipeline-run]
                                                               [run :status ~status]]}))})
                             statuses)]
    (doseq [{:keys [status count]} counts]
      (case status
        :running (prometheus/set (registry :bob/running-jobs) count)
        :passed  (prometheus/set (registry :bob/passed-jobs) count)
        :failed  (prometheus/set (registry :bob/failed-jobs) count)
        :stopped (prometheus/set (registry :bob/stopped-jobs) count)))
    (f/when-failed [err]
      err)))

(defn collect-metrics
  [queue db]
  (f/try-all [_ (->> "bob.jobs"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/queued-jobs)))
              _ (->> "bob.entities"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/queued-entities)))
              _ (->> "bob.errors"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/errors)))
              _ (count-statuses db)]
    (export/text-format registry)
    (f/when-failed [err]
      err)))
