## RabbitMQ message schemas:

Direct exchange: `bob.direct`
Queue: `bob.jobs` bound to the direct exchange

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
