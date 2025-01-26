; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.capacity
  (:require
   [clojure.string :as str]
   [common.heartbeat :as hb]
   [xtdb.api :as xt]))

(defn cluster-info
  [db]
  (-> (xt/db db)
      (xt/entity :bob.cluster/info)
      (get :data {})))

(defn ->bytes
  [mem]
  (let [[_ value unit] (re-matches #"^(\d+)([KMGTPE]i{0,1})$" mem)
        power (case unit
                ("K" "Ki") 1.0
                ("M" "Mi") 2.0
                ("G" "Gi") 3.0
                ("T" "Ti") 4.0
                ("P" "Pi") 5.0
                ("E" "Ei") 6.0
                (throw (IllegalArgumentException. (str "Invalid value: " mem))))]
    (-> (Math/pow 1024.0 power)
        (* (parse-double value))
        (long))))

(defn has-capacity?
  "Checks if the current node has capacity"
  [{{{:keys [mem]} :requests} :quotas}]
  (if-not mem
    true
    (<= (->bytes mem)
        (-> (hb/get-node-info {})
            (vals)
            (first)
            (:mem/free)))))

;; TODO: Maybe query the DB?
(defn runners-with-capacity
  "Returns all nodes with capacity"
  [db {{:keys [mem]} :requests}]
  (->> (cluster-info db)
       (filter #(str/starts-with? (key %) "bob/runner"))
       (filter (fn [[_ {:keys [:mem/free]}]]
                 (if-not mem
                   true
                   (<= (->bytes mem) free))))
       (map first)))

(comment
  (->bytes "1024Mi")

  (has-capacity? {:quotas {:requests {:mem "1024Mi"}}}))
