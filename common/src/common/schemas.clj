; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.schemas
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.string :as s]))

(spec/def :bob.pipeline/group string?)

(spec/def :bob.pipeline/name string?)

(spec/def :bob.pipeline/image string?)

(spec/def :bob.pipeline/paused boolean?)

(spec/def :bob.pipeline/vars map?)

(spec/def :bob.pipeline.resource/name string?)

(spec/def :bob.pipeline.resource-provider/name string?)

(spec/def :bob.pipeline.artifact/name string?)

(spec/def :bob.pipeline.artifact/path string?)

(spec/def :bob.pipeline.artifact/store string?)

(spec/def :bob.pipeline.step/cmd string?)

(spec/def :bob.pipeline.step/needs_resource :bob.pipeline.resource/name)

(spec/def :bob.pipeline.step/produces_artifact
  (spec/keys :req-un
             [:bob.pipeline.artifact/name
              :bob.pipeline.artifact/path
              :bob.pipeline.artifact/store]))

(spec/def :bob.pipeline.step/vars map?)

(spec/def :bob.pipeline/step
  (spec/keys :req-un [:bob.pipeline.step/cmd]
             :opt-un [:bob.pipeline.step/needs_resource
                      :bob.pipeline.step/produces_artifact
                      :bob.pipeline.step/vars]))

(spec/def :bob.pipeline/steps (spec/coll-of :bob.pipeline/step))

(spec/def :bob.pipeline.resource/type #{"external" "internal"})

(spec/def :bob.pipeline.resource/provider :bob.pipeline.resource-provider/name)

(spec/def :bob.pipeline.resource.param/repo string?)

(spec/def :bob.pipeline.resource.param/branch string?)

(spec/def :bob.pipeline.resource/params
  (spec/keys :opt-un
             [:bob.pipeline.resource.param/repo
              :bob.pipeline.resource.param/branch]))

(spec/def :bob.pipeline/resource
  (spec/keys :req-un
             [:bob.pipeline.resource/name
              :bob.pipeline.resource/type
              :bob.pipeline.resource/provider
              :bob.pipeline.resource/params]))

(spec/def :bob.pipeline/resources (spec/coll-of :bob.pipeline/resource))

(defn mem-quota?
  [s]
  (and (string? s)
       (re-matches #"^(\d+)([KMGTPE]i{0,1})$" s)))

(defn cpu-quota?
  [n]
  (and (double? n)
       (>= n 0.0)
       (<= n 1.0)))

(spec/def :bob.pipeline.quotas/cpu cpu-quota?)

(spec/def :bob.pipeline.quotas/mem mem-quota?)

(spec/def :bob.pipeline.quotas/requests
  (spec/keys :opt-un
             [:bob.pipeline.quotas/mem]))

(spec/def :bob.pipeline.quotas/limits
  (spec/keys :opt-un
             [:bob.pipeline.quotas/cpu
              :bob.pipeline.quotas/mem]))

(spec/def :bob.pipeline/quotas
  (spec/keys :opt-un
             [:bob.pipeline.quotas/requests
              :bob.pipeline.quotas/limits]))

(spec/def :bob/pipeline
  (spec/keys :req-un [:bob.pipeline/group
                      :bob.pipeline/name
                      :bob.pipeline/image
                      :bob.pipeline/steps]
             :opt-un [:bob.pipeline/vars
                      :bob.pipeline/resources
                      :bob.pipeline/quotas]))

(spec/def :bob.resource-provider/url string?)

(spec/def :bob/resource-provider (spec/keys :req-un [:bob.resource-provider/url]))

(spec/def :bob.artifact-store/url string?)

(spec/def :bob/artifact-store (spec/keys :req-un [:bob.artifact-store/url]))

(spec/def :bob.pipeline.run/scheduled-at inst?)

(spec/def :bob.pipeline.run/initiated-at inst?)

(spec/def :bob.pipeline.run/initialized-at inst?)

(spec/def :bob.pipeline.run/started-at inst?)

(spec/def :bob.pipeline.run/completed-at inst?)

(spec/def :pipeline.run/status #{:pending :initializing :initialized :running :passed :failed :stopped})

(spec/def :bob.pipeline.run/run-id (spec/and string? #(s/starts-with? % "r-")))

(spec/def :bob.pipeline/run
  (spec/keys :req-un [:bob.pipeline/group
                      :bob.pipeline/name
                      :pipeline.run/status]
             :opt-un [:bob.pipeline.run/scheduled-at
                      :bob.pipeline.run/initiated-at
                      :bob.pipeline.run/initialized-at
                      :bob.pipeline.run/started-at
                      :bob.pipeline.run/completed-at]))

(spec/def :bob.pipeline.run.log-line/time inst?)

(spec/def :bob.pipeline.run.log-line/line string?)

(spec/def :bob.pipeline.run/log-line
  (spec/keys :req-un
             [:bob.pipeline.run.log-line/time
              :bob.pipeline.run/run-id
              :bob.pipeline.run.log-line/line]))

(spec/def :bob.command.pipeline-start/type #{"pipeline/start"})

(spec/def :bob.command.pipeline-start/run-id :bob.pipeline.run/run-id)

(spec/def :bob.command.pipeline-start/data
  (spec/keys :req-un [:bob.pipeline/group
                      :bob.pipeline/name
                      :bob.command.pipeline-start/run-id]))

(spec/def :bob.command/pipeline-start
  (spec/keys :req-un
             [:bob.command.pipeline-start/type
              :bob.command.pipeline-start/data]))

(spec/def :bob.command.pipeline-stop/type #{"pipeline/stop"})

(spec/def :bob.command.pipeline-stop.data/run-id :bob.pipeline.run/run-id)

(spec/def :bob.command.pipeline-stop/data
  (spec/keys :req-un
             [:bob.command.pipeline-stop.data/run-id]))

(spec/def :bob.command/pipeline-stop
  (spec/keys :req-un
             [:bob.command.pipeline-stop/type
              :bob.command.pipeline-stop/data]))

(spec/def :bob.command.pipeline-pause/type #{"pipeline/pause"})

(spec/def :bob.command.pipeline-pause/data
  (spec/keys :req-un
             [:bob.pipeline/group
              :bob.pipeline/name]))

(spec/def :bob.command/pipeline-pause
  (spec/keys :req-un
             [:bob.command.pipeline-pause/type
              :bob.command.pipeline-pause/data]))

(spec/def :bob.command.pipeline-unpause/type #{"pipeline/unpause"})

(spec/def :bob.command.pipeline-unpause/data
  (spec/keys :req-un
             [:bob.pipeline/group
              :bob.pipeline/name]))

(spec/def :bob.command/pipeline-unpause
  (spec/keys :req-un
             [:bob.command.pipeline-unpause/type
              :bob.command.pipeline-unpause/data]))

(spec/def :bob.resource-provider/name :bob.pipeline.resource-provider/name)

(spec/def :bob.artifact-store/name :bob.pipeline.artifact/store)

(spec/def :bob.db.artifact-store/type #{:artifact-store})

(spec/def :bob.db/artifact-store
  (spec/keys :req-un
             [:bob.db.artifact-store/type
              :bob.artifact-store/url
              :bob.artifact-store/name]))

(spec/def :bob.db.resource-provider/type #{:resource-provider})

(spec/def :bob.db/resource-provider
  (spec/keys :req-un
             [:bob.db.resource-provider/type
              :bob.resource-provider/url
              :bob.resource-provider/name]))

(spec/def :bob.db.pipeline/type #{:pipeline})

(spec/def :bob.db/pipeline
  (spec/merge :bob/pipeline (spec/keys :req-un [:bob.db.pipeline/type])))

(spec/def :bob.db.log-line/type #{:log-line})

(spec/def :bob.db.log-line/line string?)

(spec/def :bob.db/log-line
  (spec/keys :req-un
             [:bob.db.log-line/type
              :bob.pipeline.run/run-id
              :bob.db.log-line/line]))

(spec/def :bob.db.log-line-event/line
  (spec/and :bob.db.log-line/line
            #(s/starts-with? % "[bob]")))

(spec/def :bob.db/log-line-event
  (spec/keys :req-un
             [:bob.db.log-line/type
              :bob.pipeline.run/run-id
              :bob.db.log-line-event/line]))

(spec/def :bob.db.run/type #{:pipeline-run})

(spec/def :bob.db/run
  (spec/merge :bob.pipeline/run
              (spec/keys :req-un [:bob.db.run/type])))

(spec/def :bob.cluster.event/at int?)
(spec/def :bob.cluster.event/type #{"Normal" "Warning" "Error"})
(spec/def :bob.cluster.event/reason string?)
(spec/def :bob.cluster.event/kind #{"Pipeline" "ResourceProvider" "ArtifactStore"})
(spec/def :bob.cluster.event/message string?)

(spec/def :bob.cluster/event
  (spec/keys :req-un
             [:bob.cluster.event/at
              :bob.cluster.event/type
              :bob.cluster.event/reason
              :bob.cluster.event/kind
              :bob.cluster.event/message]))
