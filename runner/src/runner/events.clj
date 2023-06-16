(ns runner.events
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log])
  (:import
   [com.rabbitmq.stream ConfirmationHandler]
   [com.rabbitmq.stream.impl StreamProducer]
   [java.time Instant]))

(defn publish
  [^StreamProducer producer content]
  (let [payload (.. producer
                    messageBuilder
                    properties
                    (contentType "application/json")
                    messageBuilder
                    (addData (-> content
                                 (assoc :timestamp (.toEpochMilli (Instant/now)))
                                 json/write-str
                                 .getBytes))
                    build)]
    (.send producer
           payload
           (reify ConfirmationHandler
             (handle [_ status]
               (when-not (.isConfirmed status)
                 (log/warn "Could not send message to stream"
                           {:message (.getMessage status)
                            :code (.getCode status)})))))))

(comment
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

  (publish producer payload)

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
