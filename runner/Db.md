## Crux schemas

### Log line

```clojure
{:crux.db/id :bob.pipeline.log/l-<UUID>
 :type       :log-line
 :time       #inst "UTC Date instant"
 :run-id     "r-UUID of associated run"
 :line       "this is a log line from a run"}
```

### Run

```clojure
{:crux.db/id :bob.pipeline.run/r-<UUID>
 :type       :pipeline-run
 :group      "<group-name>"
 :name       "<pipeline-name>"
 :started    #inst "UTC Date instant"
 :completed  #inst "UTC Date instant"
 :status     :<running|passed|failed|stopped|paused>}
```
