## RabbitMQ message schemas:

Direct exchange: `bob.direct`
Queue: `bob.entities` bound to the direct exchange

Error queue: `bob.errors` bound to the default exchange

### Create/Modify a [Pipeline](https://bob-cd.github.io/pages/concepts/pipeline.html)

metadata:
```json
{
  "type": "pipeline/create"
}
```
payload:
```json
{
  "group": "dev",
  "name": "test",
  "steps": [
    {
      "cmd": "echo hello"
    },
    {
      "needs_resource": "source",
      "cmd": "ls"
    }
  ],
  "vars": {
    "k1": "v1",
    "k2": "v2"
  },
  "resources": [
    {
      "name": "source",
      "type": "external",
      "provider": "git",
      "params": {
        "repo": "https://github.com/bob-cd/bob",
        "branch": "master"
      }
    }
  ],
  "image": "busybox:musl"
}
```

### Delete a Pipeline

metadata:
```json
{
  "type": "pipeline/delete"
}
```
payload:
```json
{
  "group": "dev",
  "name": "test"
}
```

### Create/Modify a [Resource Provider](https://bob-cd.github.io/pages/concepts/pipeline.html)

metadata:
```json
{
  "type": "resource-provider/create"
}
```
payload:
```json
{
  "name": "github-provider",
  "url": "http://localhost:8000"
}
```

### Delete a Resource Provider

metadata:
```json
{
  "type": "resource-provider/delete"
}
```
payload:
```json
{
  "name": "github-provider"
}
```

### Create/Modify an [Artifact Store](https://bob-cd.github.io/pages/concepts/artifact.html)

metadata:
```json
{
  "type": "artifact-store/create"
}
```
payload:
```json
{
  "name": "local-store",
  "url": "http://localhost:8001"
}
```

### Delete an Artifact Store

metadata:
```json
{
  "type": "artifact-store/delete"
}
```
payload:
```json
{
  "name": "local-store"
}
```
