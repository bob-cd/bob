; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.store
  (:require
   [babashka.http-client :as http-client]
   [clojure.data.json :as json]
   [taoensso.nippy :as nippy])
  (:import
   [io.etcd.jetcd
    ByteSequence
    Client
    KV
    KeyValue]
   [io.etcd.jetcd.kv GetResponse]
   [io.etcd.jetcd.options GetOption]
   [java.nio.charset StandardCharsets]))

(defn b
  [^String s]
  (ByteSequence/from s StandardCharsets/UTF_8))

(defn open
  [{:keys [urls]}]
  (let [client (.. (Client/builder)
                   (endpoints ^String/1 (into-array urls))
                   (build))]
    {:urls urls
     :client client
     :kv (Client/.getKVClient client)}))

(defn close
  [{:keys [client]}]
  (Client/.close client))

(defn ->data
  [kv]
  {:key (.toString (KeyValue/.getKey kv) StandardCharsets/UTF_8)
   :value (-> kv
              (KeyValue/.getValue)
              (ByteSequence/.getBytes)
              (nippy/thaw))
   :create-rev (KeyValue/.getCreateRevision kv)
   :mod-rev (KeyValue/.getModRevision kv)})

(defn get
  ([conn key]
   (get conn key {}))
  ([{:keys [kv]} key {:keys [rev prefix]}]
   (let [opts (GetOption/newBuilder)
         opts (if rev
                (.withRevision opts rev)
                opts)
         opts (if prefix
                (.isPrefix opts true)
                opts)
         res ^GetResponse @(KV/.get kv (b key) (.build opts))]
     (when-not (zero? (.getCount res))
       (map ->data (.getKvs res))))))

(defn get-one
  ([conn key]
   (:value (first (get conn key {}))))
  ([conn key opts]
   (:value (first (get conn key opts)))))

;; TODO: can txns be used here?
(defn put
  [{:keys [kv]} & {:as pairs}]
  (doseq [[k v] pairs]
    @(KV/.put kv (b k) (ByteSequence/from (nippy/freeze v)))))

(defn delete
  [{:keys [kv]} key]
  @(KV/.delete kv (b key)))

(defn ping
  [{:keys [urls]}]
  (let [gathered (->> urls
                      (map #(http-client/get (str % "/health") {:async true :timeout 2000}))
                      (doall))
        failed (eduction
                (map deref)
                (map :body)
                (map json/read-str)
                (filter #(not= "true" (% "health")))
                gathered)]
    (when (seq failed)
      (throw (ex-info "One or more DB nodes unhealthy" {})))))

(comment
  (set! *warn-on-reflection* true)

  (nippy/thaw (nippy/freeze {:foo "bar"}))

  (def c (open {:urls ["http://localhost:2379"]}))

  (put c "foo" {:foo 420})
  (put c "foo" {:foo 420 :bar 69})
  (put c "foo" {:foo 420 :bar 69 :baz 42069})
  (get c "foo")
  (get c "foo" {:rev 2})
  (delete c "foo")

  (close c)

  (let [c (open {:urls ["http://localhost:2379"]})]
    (ping c)
    (close c))

  (let [c (open {:urls ["http://localhost:2379"]})]
    (try
      (put c "foo.bar" {:foo 420})
      (put c "foo.baz" {:foo 69})
      (prn (get c "foo" {:prefix true}))
      (prn (get c "" {:prefix true}))
      (delete c "foo.bar")
      (delete c "foo.baz")
      (finally
        (close c))))

  (let [c (open {:urls ["http://localhost:2379"]})]
    (try
      (put c "foo" {:foo "bar"} "baz" [420 69])
      (prn (get c "foo"))
      (prn (get c "baz"))
      (delete c "foo")
      (delete c "baz")
      (prn (get c "foo"))
      (finally
        (close c)))))
