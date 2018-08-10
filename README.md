[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.org/bob-cd/bob.svg?branch=master)](https://travis-ci.org/bob-cd/bob)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

# Bob the Builder

## This is what CI/CD should've been.

### More [info](https://github.com/bob-cd/bob/blob/master/RATIONALE.md)

## Build requirements
- Any OS supporting Java and Docker
- JDK 8+ (latest preferred for optimal performance)
- [Leiningen](https://leiningen.org/) 2.0+

## Running requirements
- Any OS supporting Java and Docker
- JRE 8+ (latest preferred for optimal performance)
- Docker (latest preferred for optimal performance)

## Testing, building and running
- Clone this repository.
- Install the Build requirements.
- Following steps **need Docker**:
    - Run `lein test` to run tests.
    - Run `lein uberjar` to get the standalone JAR.
    - Run `java -jar ./target/bob-standalone.jar` to start the server on port **7777**.

## REST API Reference

Bob exposes a REST API which is self documented with [Swagger](https://swagger.io/).

The API docs and a simple testing client can be located on **http://0.0.0.0:7777/**

The actual API is on **http://0.0.0.0:7777/api/**

### Routes (all calls are standard HTTP requests)

- **POST** on `/api/pipeline/<group>/<name>` creates a new pipeline in a group with the specified name.
Takes list of steps and the base docker image as raw JSON POST body. 
The steps need to be in the form of `cmd [args...]`
Example of a post body:
```json
{
  "image": "busybox:musl",
  "steps": [
    "echo hello",
    "sh -c 'cat test.txt && echo test >> test.txt'"
  ]
}
```
- **GET** on `/api/pipeline/start/<group>/<name>` starts a pipeline in a group with the specified name. 
- **GET** on `/api/pipeline/stop/<group>/<name>/<number>` stops a pipeline run in a group with the specified name and number.
- **GET** on `/api/pipeline/logs/<group>/<name>/<number>/<offset>/<lines>` fetches logs for a pipeline run in a group 
with the specified name, number, starting offset and the number of lines.
- **GET** on `/api/pipeline/status/<group>/<name>/<number>` fetches the status of pipeline run in a group with the 
specified name and number.
- **DELETE** on `/api/pipeline/<group>/<name>` deletes a pipeline in a group with the specified name.
- **GET** on `/api/can-we-build-it` runs health checks for Bob, responding with
`Yes we can! 🔨 🔨` if all is well.
- **GET** on `/api/gc` runs the garbage collection for Bob, reclaiming resources.
- **GET** on `/api/gc/all` runs the full garbage collection for Bob, reclaiming **every** resource.
Use with care. **Causes full history loss!** 
