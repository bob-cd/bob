# Runner

This provides a general enough, containerised and ephemeral execution environment: [Pipeline](https://bob-cd.github.io/pipelines/) lifecycle and streaming of logs. Each pipeline begins with an initial container image and the steps are applied in order on that image to reach the final state. Multiple isolated pipeline runs could be done concurrently on a single runner.

**This is guaranteed to be [rootless](https://www.zend.com/blog/rootless-containers); ideal for usage in cloud native environments.**

## How does this work

- This is implemented in Clojure/JVM
- Uses [RabbitMQ](https://www.rabbitmq.com/) to receive messages and perform the necessary effects as well as producing events via stream
- Uses [etcd](https://etcd.io/) for cluster state and coordination
- Uses [contajners](https://github.com/lispyclouds/contajners) to talk to [podman](https://podman.io/) to implement step executions

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
| BOB_CONNECTION_RETRY_ATTEMPTS | 10                                               |
| BOB_CONNECTION_RETRY_DELAY    | 2000                                             |
| BOB_STREAM_NAME               | bob.event-stream                                 |
| BOB_STREAM_URL                | rabbitmq-stream://guest:guest@localhost:5552/%2f |
| BOB_STREAM_RETENTION_DAYS     | 7                                                |
| CONTAINER_ENGINE_URL          | http://localhost:8080                            |

## Building and Running

### Requirements, min versions, latest recommended.

- JDK 19+
- RabbitMQ 3.13+ with the [management-plugin](https://www.rabbitmq.com/docs/management)
- etcd 3.6+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)
- [Babashka](https://github.com/babashka/babashka#installation)

### Ways of connecting Runner to the cluster

- To build an uberjar run `bb compile` to obtain a `runner.jar`. Running `java --enable-preview -jar runner.jar` should connect to it.
- To run directly without building a JAR, run `clojure -J--enable-preview -M -m runner.main` from this dir.

## Setting up the dev environment with the REPL

- This uses [Integrant](https://github.com/weavejester/integrant) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/runner/src/runner/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests

Run `bb test` from this dir. (needs docker)
