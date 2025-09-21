# API Server

This is the coherent gateway for the Bob cluster having the REST API, schema checks, health checks for all the services and useful overviews like system status and metrics.

## How does this work

- This is implemented in Clojure/JVM
- Implements a [spec-first](https://www.atlassian.com/blog/technology/spec-first-api-development) REST API with this OpenAPI 3.0+ [schema](/apiserver/src/main/resources/bob/api.yaml)
- Uses [RabbitMQ](https://www.rabbitmq.com/) to send the requests from the API and receive events from the runner via RabbitMQs streams interface
- Uses [etcd](https://etcd.io/) for cluster state and coordination

## Configuration

[Aero](https://github.com/juxt/aero) is used and therefore several variables can be set by specifying them as environment variables. Possible variables are:

| Environment variables         | defaults                                         |
| ----------------------------- | ------------------------------------------------ |
| BOB_STORAGE_URLS              | http://localhost:2379                            |
| BOB_STORAGE_USER              | bob                                              |
| BOB_STORAGE_PASSWORD          | bob                                              |
| BOB_QUEUE_URL                 | amqp://localhost:5672                            |
| BOB_QUEUE_USER                | guest                                            |
| BOB_QUEUE_PASSWORD            | guest                                            |
| BOB_QUEUE_API_URL             | http://localhost:15672/api                       |
| BOB_API_HOST                  | 0.0.0.0                                          |
| BOB_API_PORT                  | 7777                                             |
| BOB_HEALTH_CHECK_FREQ         | 10000                                            |
| BOB_CONNECTION_RETRY_ATTEMPTS | 10                                               |
| BOB_CONNECTION_RETRY_DELAY    | 2000                                             |
| BOB_STREAM_NAME               | bob.event-stream                                 |
| BOB_STREAM_URL                | rabbitmq-stream://guest:guest@localhost:5552/%2f |
| BOB_STREAM_RETENTION_DAYS     | 7                                                |

## Building and Running

### Requirements, min versions, latest recommended.

- JDK 19+
- RabbitMQ 3.13+ with the [management-plugin](https://www.rabbitmq.com/docs/management)
- etcd 3.6+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)
- [Babashka](https://github.com/babashka/babashka#installation)
- A bob logger like [logger-local](https://github.com/bob-cd/logger-local)
- (Optional) A bob artifact store like [artifact-local](https://github.com/bob-cd/artifact-local)

### Ways of connecting APIServer to the cluster

- To build an uberjar run `bb compile` to obtain an `apiserver.jar`. Running `java --enable-preview -jar apiserver.jar` should connect to it.
- To run directly without building a JAR, run `clojure -J--enable-preview -M -m apiserver.main` from this dir.

## Setting up the dev environment with the REPL

- This uses [Integrant](https://github.com/weavejester/integrant) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/apiserver/src/apiserver/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests

Run `bb test` from this dir. (needs docker)
