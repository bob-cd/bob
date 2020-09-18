## RabbitMQ message schemas:

This listens on the queue: `jobs`

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
