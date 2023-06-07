(ns apiserver.events
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [ring.core.protocols :as p])
  (:import
   [com.rabbitmq.stream MessageHandler OffsetSpecification]))

(defn events-handler
  [{{:keys [environment name]} :stream}]
  {:status 200
   :headers {"content-type" "text/event-stream"
             "transfer-encoding" "chunked"}
   :body (reify p/StreamableResponseBody
           (write-body-to-stream [_ _ output-stream]
             (with-open [w (io/writer output-stream)]
               (let [complete (promise)
                     consumer (.. environment
                                  consumerBuilder
                                  (stream name)
                                  (offset (OffsetSpecification/first))
                                  (messageHandler (reify MessageHandler
                                                    (handle [_ _ message]
                                                      (try
                                                        (let [creation-time (.getCreationTime (.getProperties message))
                                                              out-str (-> (.getBodyAsBinary message)
                                                                          String.
                                                                          json/read-str
                                                                          (assoc :timestamp creation-time)
                                                                          json/write-str
                                                                          ;; SSE format: data: foo\n\n
                                                                          (format "data: %s\n\n"))]
                                                          (doto w
                                                            (.write out-str)
                                                            (.flush)))
                                                        (catch Exception _
                                                          (println "client disconnected")
                                                          (deliver complete :done)))))) ;; unblock
                                  build)]
                 @complete ;; block til done
                 (.close consumer)
                 (.close output-stream)))))})
