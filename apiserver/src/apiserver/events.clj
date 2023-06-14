(ns apiserver.events
  (:require
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
                                                        (doto w
                                                          (.write (str "data: " (String. (.getBodyAsBinary message)) "\n\n")) ;; SSE format: data: foo\n\n
                                                          (.flush))
                                                        (catch Exception _
                                                          (println "client disconnected")
                                                          (deliver complete :done)))))) ;; unblock
                                  build)]
                 @complete ;; block til done
                 (.close consumer)
                 (.close output-stream)))))})
