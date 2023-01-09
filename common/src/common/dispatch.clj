; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.dispatch
  (:require
   [clojure.data.json :as json]
   [common.errors :as err]
   [failjure.core :as f]
   [taoensso.timbre :as log]))

(defn queue-msg-subscriber
  [db-client routes chan meta-data payload]
  (let [msg (f/try* (json/read-str (String. payload "UTF-8") :key-fn keyword))]
    (if (f/failed? msg)
      (err/publish-error chan (format "Could not parse '%s' as json" (String. payload "UTF-8")))
      (do
        (log/infof "payload %s, meta: %s"
                   msg
                   meta-data)
        (if-let [routed-fn (some-> meta-data
                                   :type
                                   routes)]
          (routed-fn db-client chan msg)
          (err/publish-error chan (format "Could not route message: %s" msg)))))))
