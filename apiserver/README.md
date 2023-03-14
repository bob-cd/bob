# API Server

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-611661635), this is the service that is responsible for implementing the REST API for Bob.

This is intended as the coherent gateway for the Bob cluster having the schema checks, health checks for all the services and useful overviews like system status and metrics.

## How does this work
- This is implemented in Clojure/JVM
- Implements a [spec-first](https://www.atlassian.com/blog/technology/spec-first-api-development) REST API with this OpenAPI 3.0+ [schema](/apiserver/src/main/resources/bob/api.yaml)
- Uses [RabbitMQ](https://www.rabbitmq.com/) to send the requests from the API and receive events from the runner via RabbitMQs streams interface
- Uses [XTDB](https://xtdb.com) backed by [PostgreSQL](https://www.postgresql.org/) for reading the cluster state

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
| BOB_QUEUE_API_URL             | http://localhost:15672/api                        |
| BOB_API_HOST                  | 0.0.0.0                                           |
| BOB_API_PORT                  | 7777                                              |
| BOB_HEALTH_CHECK_FREQ         | 60000                                             |
| BOB_CONNECTION_RETRY_ATTEMPTS | 10                                                |
| BOB_CONNECTION_RETRY_DELAY    | 2000                                              |
| BOB_STREAM_NAME               | bob.stream                                        |
| BOB_STREAM_URL                | rabbitmq-stream://guest:guest@localhost:5552/%2f" |
| BOB_STREAM_RETENTION_DAYS     | 7                                                 |

## Building and Running

### Requirements, min versions, latest recommended.
- JDK 19+
- RabbitMQ 3.8+
- PostgreSQL 11+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)
- [Babashka](https://github.com/babashka/babashka#installation)
- (Optional) A bob artifact store like [artifact-local](https://github.com/bob-cd/artifact-local)

### Using Docker to easily boot up a local cluster
- Install Docker 18+ and start it up
- Run `docker run -it --name bob-queue -p 5672:5672 -p 15672:15672 -p 5552:5552 rabbitmq:3-management-alpine` to run the latest management enabled RabbitMQ instance on port `5672`, the streams interface on port `5552` and the admin control on port `15672`. The default credentials are `guest:guest`.
- Run `docker exec bob-queue rabbitmq-plugins enable rabbitmq_stream` to enable the stream plugin on the RabbitMQ instance.
- Run `docker run --rm -it --name bob-storage -p 5432:5432 -e POSTGRES_DB=bob -e POSTGRES_USER=bob -e POSTGRES_PASSWORD=bob postgres:alpine` to run the latest PostgreSQL instance on port `5432`.

### Ways of connecting APIServer to the cluster
- To build an uberjar run `bb compile` to obtain an `apiserver.jar`. Running `java -jar apiserver.jar` should connect to it all nicely.
- To run directly without building a JAR, run `clojure -J--enable-preview -M -m apiserver.main` from this dir.

## Setting up the dev environment with the REPL
- This uses [Integrant](https://github.com/weavejester/integrant) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/apiserver/src/apiserver/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests
Run `bb test` from this dir. (needs docker)
