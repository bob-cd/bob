(ns common.heartbeat
  (:require
   [babashka.http-client :as http-client]
   [clojure.data.json :as json]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [common.store :as store]
   [failjure.core :as f])
  (:import
   [com.sun.management OperatingSystemMXBean] ;; TODO: Maybe something other than com.sun.*?
   [java.lang.management ManagementFactory]
   [java.util.concurrent Executors TimeUnit]))

(defn get-node-info
  [extras]
  (let [bean ^OperatingSystemMXBean (ManagementFactory/getOperatingSystemMXBean)]
    (merge {:cpu/count (Runtime/.availableProcessors (Runtime/getRuntime))
            :cpu/load (.getSystemCpuLoad bean)
            :mem/free (.getFreePhysicalMemorySize bean)
            :mem/total (.getTotalPhysicalMemorySize bean)}
           extras)))

(defn get-connections
  [timeout {:keys [api-url username password]}]
  (let [data (-> (str api-url "/connections")
                 (http-client/get {:basic-auth [username password] :timeout timeout})
                 (:body)
                 (json/read-str {:key-fn keyword}))]
    (->> data
         (map #(get-in % [:client_properties :connection_name]))
         (filter #(str/starts-with? % "bob/"))
         (into #{}))))

(defn beat-it
  [db queue-info timeout node-id & {:as extra-data}]
  (f/try-all [id "bob.cluster/info"
              node-info {node-id (get-node-info extra-data)}
              cluster (-> (store/get-one db id)
                          (get :data {}))
              cleaned (->> (get-connections timeout queue-info)
                           (set/difference (set (keys cluster)))
                           (apply dissoc cluster))]
    (store/put db id {:data (merge cleaned node-info)})
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

  (get-node-info {})

  (get-connections 10000
                   {:api-url "http://localhost:15672/api"
                    :username "guest"
                    :password "guest"}))
