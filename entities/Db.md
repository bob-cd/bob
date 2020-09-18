## Crux schemas

### [Pipeline](https://bob-cd.github.io/pages/concepts/pipeline.html)

```clojure
{:crux.db/id :bob.pipeline.test/test
 :type       :pipeline
 :steps      [{:cmd "echo hello"}
              {:needs_resource "source"
               :cmd            "ls"}]
 :vars       {:k1 "v1"
              :k2 "v2"}
 :resources  [{:name    "source"
              :type     "external"
              :provider "git"
              :params   {:repo   "https://github.com/bob-cd/bob"
                         :branch "master"}}]
 :image      "busybox:musl"}
```

### [Resource Provider](https://bob-cd.github.io/pages/concepts/resource.html)

```clojure
{:crux.db/id :bob.pipeline.resource-provider/github-provider
 :type       :resource-provider
 :url        "http://localhost:8000"}
```

### [Artifact Store](https://bob-cd.github.io/pages/concepts/artifact.html)

```clojure
{:crux.db/id :bob.pipeline.artifact-store/local-store
 :type       :artifact-store
 :url        "http://localhost:8001"}
```
