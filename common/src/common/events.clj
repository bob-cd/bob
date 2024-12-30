; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.events
  (:require
   [clojure.data.json :as json]
   [clojure.spec.alpha :as spec]
   [clojure.tools.logging :as log]
   [common.schemas])
  (:import
   [com.rabbitmq.stream ConfirmationHandler]
   [com.rabbitmq.stream.impl StreamProducer]
   [java.time Instant]))

(defn emit
  [^StreamProducer producer event]
  (let [timestamped (assoc event :at (.toEpochMilli (Instant/now)))
        payload (when (spec/valid? :bob.cluster/event timestamped)
                  (.. producer
                      messageBuilder
                      properties
                      (contentType "application/json")
                      messageBuilder
                      (addData (-> timestamped
                                   json/write-str
                                   .getBytes))
                      build))]
    (if-not payload
      (log/errorf "Event didn't match expected shape, ignoring %s" timestamped)
      (.send producer
             payload
             (reify ConfirmationHandler
               (handle [_ status]
                 (when-not (.isConfirmed status)
                   (log/warn "Could not send message to stream"
                             {:message (.getMessage status)
                              :code (.getCode status)}))))))))

(comment
  (set! *warn-on-reflection* true)

  (import '[com.rabbitmq.stream OffsetSpecification ConfirmationHandler Environment MessageHandler])

  (def stream "bob.stream")

  (def environment (.. Environment builder build))

  (def producer (.. environment
                    producerBuilder
                    (stream stream)
                    (name "bob-producer")
                    build))

  (def payload {:foo :bar :baz :meh})

  (.. environment
      streamCreator
      (stream stream)
      create)

  (emit producer payload)

  (def consumer (.. environment
                    consumerBuilder
                    (stream stream)
                    (offset (OffsetSpecification/first))
                    (messageHandler (reify MessageHandler
                                      (handle [_ _ message]
                                        (let [creation-time (.getCreationTime (.getProperties message))
                                              out-str (-> (.getBodyAsBinary message)
                                                          String.
                                                          json/read-str
                                                          (assoc :timestamp creation-time)
                                                          json/write-str
                                                          ;; SSE format: data: foo\n\n
                                                          (#(str "data: " % "\n\n")))]
                                          (println "MESSAGE: " out-str)))))
                    build)))
