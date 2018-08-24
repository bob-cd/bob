## REST API Reference

Bob exposes a REST API which is self documented with [Swagger](https://swagger.io/).

The API docs and a simple testing client can be located on **http://localhost:7777/**

### Routes (all calls are standard HTTP requests)

- **POST** on `/api/pipeline/<group>/<name>` creates a new pipeline in a group with the specified name.
Takes list of steps and the base docker image and a list of environment vars as raw JSON POST body. 
The steps need to be in the form of `cmd [args...]`
Example of a post body:
```json
{
  "image": "busybox:musl",
  "vars": [
    {"env": "test"},
    {"url": "test.com"}
  ],
  "steps": [
    "echo hello",
    "sh -c 'touch test.txt && echo $env >> test.txt'",
    "cat test.txt"
  ]
}
```
- **POST** on `/api/pipeline/start/<group>/<name>` starts a pipeline in a group with the specified name. 
- **POST** on `/api/pipeline/stop/<group>/<name>/<number>` stops a pipeline run in a group with the specified name and number.
- **GET** on `/api/pipeline/logs/<group>/<name>/<number>/<offset>/<lines>` fetches logs for a pipeline run in a group 
with the specified name, number, starting offset and the number of lines.
- **GET** on `/api/pipeline/status/<group>/<name>/<number>` fetches the status of pipeline run in a group with the 
specified name and number.
- **DELETE** on `/api/pipeline/<group>/<name>` deletes a pipeline in a group with the specified name.
- **GET** on `/api/can-we-build-it` runs health checks for Bob, responding with
`Yes we can! 🔨 🔨` if all is well.
- **POST** on `/api/gc` runs the garbage collection for Bob, reclaiming resources.
- **POST** on `/api/gc/all` runs the full garbage collection for Bob, reclaiming **every** resource.
Use with care. **Causes full history loss!** 
