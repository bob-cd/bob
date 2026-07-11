; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.store
  (:require
   [claxon.client :as nats]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [taoensso.nippy :as nippy])
  (:import
   [clojure.lang ExceptionInfo]
   [java.util Base64]))

(defn- b64-decode
  ^bytes [^String s]
  (.decode (Base64/getDecoder) s))

(defn- kv-prefix
  [bucket]
  (str "$KV." bucket "."))

(defn- k->sub
  [bucket k]
  (str (kv-prefix bucket) k))

(defn- sub->k
  [bucket sub]
  (->> bucket
       (kv-prefix)
       (count)
       (subs sub)))

(defn- decode-headers
  [hdrs]
  (when hdrs
    (->> hdrs
         (b64-decode)
         (String.)
         (str/split-lines)
         (rest) ;; skip the "NATS/1.0" version line
         (take-while (complement str/blank?))
         (reduce (fn [acc line]
                   (let [[k v] (str/split line #": " 2)]
                     (update acc k (fnil conj []) v)))
                 {}))))

(defn- ded?
  [hdrs]
  (contains? #{["DEL"] ["PURGE"]}
             (get hdrs "KV-Operation")))

(defn- req
  ([conn sub body]
   (req conn sub body "PUB" nil))
  ([conn sub body op headers]
   (let [inbox (str "_INBOX." (random-uuid))
         p (promise)
         rh (nats/add-handler conn
                              (fn [frame _conn] (deliver p frame))
                              {:op "MSG" :args {:subject inbox}})]
     (nats/invoke conn {:op "SUB" :args {:subject inbox :sid inbox}})
     (nats/invoke conn (cond-> {:op op
                                :args {:subject sub :reply-to inbox}
                                :payloads {:body body}}
                         headers (assoc-in [:payloads :headers] {:headers headers})))
     (let [res (deref p (:bob/timeout conn) :timeout)]
       (nats/remove-handler conn rh)
       (nats/invoke conn {:op "UNSUB" :args {:sid inbox}})
       res))))

(defn- jreq
  [conn sub body]
  (let [res (req conn sub (json/write-str body))]
    (when (= res :timeout)
      (throw (ex-info "JetStream API request timed out"
                      {:subject sub :body body})))
    (let [body (json/read-str (String. ^bytes (:body res)))]
      (when-let [err (get body "error")]
        (throw (ex-info (get err "description" "JetStream API error")
                        {:error err
                         :code (get err "code")})))
      body)))

(defn- msg->data
  [parsed-message]
  (let [{:strs [data hdrs]} parsed-message]
    (when-not (ded? (decode-headers hdrs))
      (some-> data b64-decode nippy/thaw))))

(defn- get-current
  [conn bucket sub]
  (let [resp (try
               (jreq conn
                     (str "$JS.API.STREAM.MSG.GET.KV_" bucket)
                     {"last_by_subj" sub})
               (catch ExceptionInfo e
                 (when-not (= (-> e ex-data :code) 404)
                   (throw e))))]
    (get resp "message")))

(defn- get-by-rev
  [conn bucket sub rev]
  (let [resp (try
               (jreq conn
                     (str "$JS.API.STREAM.MSG.GET.KV_" bucket)
                     {"seq" rev})
               (catch ExceptionInfo e
                 (when-not (= (-> e ex-data :code) 404)
                   (throw e))))
        msg (get resp "message")]
    (when (and msg (= (msg "subject") sub))
      msg)))

(defn- stream
  [conn bucket deliver-policy filter-subject f]
  (let [sid (str "_INBOX." (random-uuid) ".push")
        results (atom [])
        received (atom 0)
        done (promise)
        handler (partial f results received)]
    (nats/invoke conn {:op "SUB" :args {:subject sid :sid sid}})
    (let [mh (nats/add-handler conn handler {:op "MSG" :args {:sid sid}})
          hh (nats/add-handler conn handler {:op "HMSG" :args {:sid sid}})]
      (try
        (let [stream-name (str "KV_" bucket)
              resp (jreq conn
                         (str "$JS.API.CONSUMER.CREATE." stream-name)
                         {"stream_name" stream-name
                          "config" {"deliver_subject" sid
                                    "deliver_policy" deliver-policy
                                    "ack_policy" "none"
                                    "replay_policy" "instant"
                                    "filter_subject" filter-subject}})
              n (get resp "num_pending" 0)]
          (when (> n 0)
            ;; we dont know how long things are coming, catch them here
            (add-watch received
                       :done
                       (fn [_ _ _ new-val]
                         (when (= new-val n)
                           (deliver done true))))
            (when (= @received n)
              (deliver done true))
            (let [result (deref done (:bob/timeout conn) :timeout)]
              (remove-watch received :done)
              (when (= result :timeout)
                (throw (ex-info "stream timed out" {:bucket bucket})))
              @results)))
        (finally
          (nats/remove-handler conn mh)
          (nats/remove-handler conn hh)
          (nats/invoke conn {:op "UNSUB" :args {:sid sid}}))))))

(def buckets ["bob_pipeline"
              "bob_resource-provider"
              "bob_artifact-store"
              "bob_logger"
              "bob_pipeline_run"])

(defn open
  [{:keys [urls timeout max-history]}]
  (let [conn (-> {:claxon/urls urls}
                 (nats/connect)
                 (assoc :bob/timeout timeout))]
    (doseq [bucket buckets]
      (let [stream (str "KV_" bucket)]
        (jreq conn
              (str "$JS.API.STREAM.CREATE." stream)
              {"name" stream
               "subjects" [(str "$KV." bucket ".>")]
               "max_msgs_per_subject" max-history
               "discard" "old"
               "deny_delete" true
               "allow_rollup_hdrs" true})))
    conn))

(defn kv-get
  ([conn bucket k]
   (kv-get conn bucket k {}))
  ([conn bucket k {:keys [rev]}]
   (let [sub (k->sub bucket k)
         msg (if rev
               (get-by-rev conn bucket sub rev)
               (get-current conn bucket sub))]
     (when msg
       (when-let [data (msg->data msg)]
         data)))))

(defn kv-put
  [conn bucket k v]
  (let [res (req conn (k->sub bucket k) (nippy/freeze v))]
    (when (= res :timeout)
      (throw (ex-info "kv-put timed out" {:bucket bucket :key k})))
    (let [ack (json/read-str (String. ^bytes (:body res)))]
      (when-let [err (get ack "error")]
        (throw (ex-info (get err "description" "kv-put error")
                        {:error err}))))))

(defn kv-del
  [conn bucket k]
  (let [res (req conn (k->sub bucket k) nil "HPUB" {"KV-Operation" ["DEL"]})]
    (when (= res :timeout)
      (throw (ex-info "kv-del timed out" {:bucket bucket :key k})))
    (let [ack (json/read-str (String. ^bytes (:body res)))]
      (when-let [err (get ack "error")]
        (throw (ex-info (get err "description" "kv-del error")
                        {:error err}))))))

(defn kv-list
  [conn bucket]
  (stream conn
          bucket
          "last_per_subject"
          (str "$KV." bucket ".>")
          (fn [results received frame _conn]
            (when-not (-> frame :headers :headers ded?)
              (let [sub (get-in frame [:args :subject])]
                (swap! results
                       conj
                       {:key (sub->k bucket sub)
                        :value (-> frame :body nippy/thaw)})))
            (swap! received inc))))

(defn kv-history
  [conn bucket k]
  (stream conn
          bucket
          "all"
          (k->sub bucket k)
          (fn [results received frame _conn]
            (when-not (-> frame :headers :headers ded?)
              (swap! results
                     conj
                     {:value (-> frame :body nippy/thaw)
                      :rev (-> frame
                               :args
                               :reply-to
                               (str/split #"\.")
                               (nth 5) ;; Looks like this:"$JS.ACK.KV_b1.9lNv5kQc.1.8.7.1783453242346153058.1"
                               parse-long)}))
            (swap! received inc))))

(defn ping
  [conn]
  (nats/invoke conn {:op "PING"}))

(defn close
  [conn]
  (nats/close conn))

(comment
  (set! *warn-on-reflection* true)

  (nippy/thaw (nippy/freeze {:foo "bar"}))

  (def c (open {:urls ["nats://localhost:4222"]
                :timeout 2000
                :max-history 64}))

  (ping c)

  (kv-put c "bob_pipeline" "foo" {:foo 42069})
  (kv-put c "bob_pipeline" "foo" {:foo 69})
  (kv-put c "bob_pipeline" "foo2" {:foo 420 :bar 69})
  (kv-put c "bob_logger" "foo" {:foo 420 :bar 69 :baz 42069})
  (kv-get c "bob_pipeline" "foo")
  (kv-get c "bob_pipeline" "foo2")
  (kv-get c "bob_pipeline" "foo3")
  (kv-get c "bob_pipeline" "foos")
  (kv-get c "bob_pipeline" "foo" {:rev 1})
  (kv-get c "bob_logger" "foo")
  (kv-list c "bob_pipeline")
  (kv-history c "bob_pipeline" "foo")
  (kv-del c "bob_pipeline" "foo")

  (close c))
