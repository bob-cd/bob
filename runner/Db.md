## Crux schemas

### Log line

```clojure
{:crux.db/id :bob.pipeline.log/l-<UUID>
 :type       :log-line
 :run-id     "<UUID of associated run>"
 :line       "this is a log line from a run"}
```

### Run

```clojure
{:crux.db/id :bob.pipeline.run/r-<UUID>
 :type       :pipeline-run
 :group      "<group-name>"
 :name       "<pipeline-name>"
 :status     :<running|passed|failed>}
```
