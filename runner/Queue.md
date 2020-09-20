## RabbitMQ message schemas:

Direct exchange: `bob.direct`
Queue: `bob.jobs` bound to the direct exchange

Fanout exchange: `bob.fanout`
Queue: `bob.broadcasts.<unique id for runner>` bound to the fanout exchange

Error queue: `bob.errors` bound to the default exchange

### Start a [Pipeline](https://bob-cd.github.io/pages/concepts/pipeline.html)

metadata:
```json
{
  "type": "pipeline/start"
}
```
payload:
```json
{
  "group": "dev",
  "name": "test"
}
```

### Stop a Pipeline

This must be put on the `bob.fanout` exchange to be broadcasted to all runners.

metadata:
```json
{
  "type": "pipeline/stop"
}
```
payload:
```json
{
  "group": "dev",
  "name": "test",
  "run_id": "r-unique_run_uuid"
}
```
