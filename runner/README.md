# Runner

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-611661635), this is the service that is responsible for performing the **starting, stopping, pausing and streaming logs** of [Pipelines](https://bob-cd.github.io/pages/concepts/pipeline.html).

This provides a general enough, isolated and ephemeral execution environment. Each pipeline must begin with a starting image and the steps are applied in order on that image to reach the final state.

**This is guaranteed to be [rootless](https://www.zend.com/blog/rootless-containers); ideal for usage in cloud native environments.**

## How does this work
- This is implemented in Clojure/JVM
- Uses [RabbitMQ](https://www.rabbitmq.com/) to receive messages and perform the necessary effects as well as producing events via stream
- Uses [XTDB](https://xtdb.com) backed by [PostgreSQL](https://www.postgresql.org/) for temporal persistence
- Uses [contajners](https://github.com/lispyclouds/contajners) to talk to [podman](https://podman.io/) to implement step executions.

## Configuration
[Aero](https://github.com/juxt/aero) is used and therefore several variables can be set by specifying them as environment variables. Possible variables are:

| Environment variables         | defaults                                          |
|-------------------------------|---------------------------------------------------|
| BOB_STORAGE_URL               | jdbc:postgresql://localhost:5432/bob              |
| BOB_STORAGE_USER              | bob                                               |
| BOB_STORAGE_PASSWORD          | bob                                               |
| BOB_QUEUE_URL                 | amqp://localhost:5672                             |
| BOB_QUEUE_USER                | guest                                             |
| BOB_QUEUE_PASSWORD            | guest                                             |
| BOB_CONNECTION_RETRY_ATTEMPTS | 10                                                |
| BOB_CONNECTION_RETRY_DELAY    | 2000                                              |
| BOB_STREAM_NAME               | bob.event-stream                                  |
| BOB_STREAM_URL                | rabbitmq-stream://guest:guest@localhost:5552/%2f" |
| BOB_STREAM_RETENTION_DAYS     | 7                                                 |
| CONTAINER_ENGINE_URL          | http://localhost:8080                             |

## Building and Running

### Requirements, min versions, latest recommended.
- JDK 19+
- RabbitMQ 3.8+
- PostgreSQL 11+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)
- [Babashka](https://github.com/babashka/babashka#installation)

### Using Docker to easily boot up a local cluster
- Install Docker 18+ and start it up
- Run `docker run -it --name bob-queue -p 5672:5672 -p 15672:15672 -p 5552:5552  -e RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS='-rabbitmq_stream advertised_host localhost' --entrypoint sh rabbitmq:management-alpine -c 'rabbitmq-plugins enable --offline rabbitmq_stream && rabbitmq-server'` to run the latest management enabled RabbitMQ instance on port `5672`, the streams interface on port `5552` and the admin control on port `15672`. The default credentials are `guest:guest`.
- Run `docker exec bob-queue rabbitmq-plugins enable rabbitmq_stream` to enable the stream plugin on the RabbitMQ instance.
- Run `docker run --rm -it --name bob-storage -p 5432:5432 -e POSTGRES_DB=bob -e POSTGRES_USER=bob -e POSTGRES_PASSWORD=bob postgres:alpine` to run the latest PostgreSQL instance on port `5432`.

### Ways of connecting Runner to the cluster
- To build an uberjar run `bb compile` to obtain a `runner.jar`. Running `java -jar runner.jar` should connect to it all nicely.
- To run directly without building a JAR, run `clojure -J--enable-preview -M -m runner.main` from this dir.

## Setting up the dev environment with the REPL
- This uses [Integrant](https://github.com/weavejester/integrant) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/runner/src/runner/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests
Run `bb test` from this dir. (needs docker)
