; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.runs
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [common.capacity :as cp]
   [common.events :as ev]
   [langohr.basic :as lb]
   [xtdb.api :as xt]))

(defn- publish
  [ch exhchange queue-name msg msg-type]
  (lb/publish ch
              exhchange
              queue-name
              (json/write-str msg)
              {:content-type "application/json"
               :type msg-type}))

(defn dispatch-start
  ([db ch pipeline run-id]
   (dispatch-start db ch pipeline run-id 2000))
  ([db ch {:keys [group name quotas]} run-id backoff]
   (let [nodes (cp/nodes-with-capacity db quotas)
         cmd {:group group :name name :run-id run-id :backoff backoff}
         msg-type "pipeline/start"]
     (if (empty? nodes)
       (publish ch
                "bob.dlx"
                "bob.dlq"
                cmd
                msg-type)
       (publish ch
                "bob.direct"
                (str "bob.jobs." (rand-nth nodes)) ; To ensure a normal distribution
                cmd
                msg-type)))))

(defn dispatch-stop
  [db ch run-id]
  (let [id (->> (cp/cluster-info db)
                (filter (fn [[_ info]]
                          (contains? (set (:bob/runs info)) run-id)))
                (map first))]
    (when id
      (publish ch
               "bob.direct"
               (str "bob.jobs." id)
               {:run-id run-id}
               "pipeline/stop"))))

(defn retry
  "Receives messages from the DLQ and retries with a backoff interval.

  Retries by requeueing to the job queue if still pending."
  [{:keys [database stream]} ch {:keys [delivery-tag]} ^bytes payload]
  (let [{:keys [group name run-id backoff]} (json/read-str (String/new payload "UTF-8") :key-fn keyword)
        {:keys [status]} (xt/entity (xt/db database) (keyword "bob.pipeline.run" run-id))]
    (lb/ack ch delivery-tag)
    (when (= :pending status) ; check this as it could be stopped (cancelled) by user
      (let [to-log (format "Retrying run %s in %dms" run-id backoff)]
        (log/info to-log)
        (ev/emit (:producer stream)
                 {:kind "Pipeline"
                  :type "Normal"
                  :reason "PipelineRunRetry"
                  :message to-log})
        (future
          (Thread/sleep backoff)
          (dispatch-start
           database
           ch
           (xt/entity (xt/db database) (keyword (str "bob.pipeline." group) name))
           run-id
           (* backoff 2)))))))
