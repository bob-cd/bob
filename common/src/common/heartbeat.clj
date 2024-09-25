(ns common.heartbeat
  (:require
   [babashka.http-client :as http-client]
   [clojure.data.json :as json]
   [clojure.set :as set]
   [clojure.tools.logging :as log]
   [failjure.core :as f]
   [xtdb.api :as xt])
  (:import
   [com.sun.management OperatingSystemMXBean] ;; TODO: Maybe something other than com.sun.*?
   [java.lang.management ManagementFactory]
   [java.net InetAddress]
   [java.util.concurrent Executors TimeUnit]))

(defn get-node-info
  [extras]
  (let [bean ^OperatingSystemMXBean (ManagementFactory/getOperatingSystemMXBean)]
    {(.getHostAddress (InetAddress/getLocalHost))
     (merge {:cpu/load (.getSystemCpuLoad bean)
             :mem/free (.getFreePhysicalMemorySize bean)
             :mem/total (.getTotalPhysicalMemorySize bean)}
            extras)}))

(defn get-connections
  [{:keys [api-url username password]}]
  (let [data (-> (str api-url "/connections")
                 (http-client/get {:basic-auth [username password]})
                 (:body)
                 (json/read-str {:key-fn keyword}))]
    (->> data
         (map :peer_host)
         (into #{}))))

(defn beat-it
  [db queue-info & {:as extra-data}]
  (f/try-all [id :bob.cluster/info
              node-info (get-node-info extra-data)
              cluster (get (xt/entity (xt/db db) id) :data {}) ;; TODO: Assert the cluster-info spec
              cleaned (->> (get-connections queue-info)
                           (set/difference (set (keys cluster)))
                           (apply dissoc cluster))]
    (xt/await-tx db (xt/submit-tx db [[::xt/put {:xt/id id :data (merge cleaned node-info)}]]))
    (f/when-failed [err]
      (log/errorf "Error sending heartbeat to %s: %s" (:api-url queue-info) err))))

(defn schedule
  [the-fn kind freq]
  (let [vfactory (.. (Thread/ofVirtual)
                     (name (str kind "-vthread-") 0)
                     (factory))]
    (.scheduleAtFixedRate (Executors/newSingleThreadScheduledExecutor vfactory)
                          the-fn
                          0
                          freq
                          TimeUnit/MILLISECONDS)))

(comment
  (set! *warn-on-reflection* true)

  (import '[xtdb.api IXtdb])

  (get-node-info {})

  (get-connections {:api-url "http://localhost:15672/api"
                    :username "guest"
                    :password "guest"})

  (def node
    (xt/start-node {:xtdb.jdbc/connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.psql/->dialect}
                                                :db-spec {:jdbcUrl "jdbc:postgresql://localhost:5432/bob"
                                                          :user "bob"
                                                          :password "bob"}}
                    :xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                                  :connection-pool :xtdb.jdbc/connection-pool}
                    :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                                          :connection-pool :xtdb.jdbc/connection-pool}}))

  (IXtdb/.close node)

  (xt/entity (xt/db node) :bob.cluster/info)

  (beat-it node
           {:api-url "http://localhost:15672/api"
            :username "guest"
            :password "guest"}
           :bob/node-type :runner :bob/workload [:foo]))
