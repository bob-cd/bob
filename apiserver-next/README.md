# API Server

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-611661635), this is the service that is responsible for implementing the REST API for Bob.

This is intended as the coherent gateway for the Bob cluster having the schema checks, health checks for all the services and useful overviews like system status and metrics.

## How does this work
- This is implemented in Clojure/JVM
- Implements a [spec-first](https://www.atlassian.com/blog/technology/spec-first-api-development) REST API with this OpenAPI 3.0+ [schema](/apiserver/src/main/resources/bob/api.yaml)
- Uses [RabbitMQ](https://www.rabbitmq.com/) to send the requests from the API
- Uses [Crux](https://www.opencrux.com/) backed by [PostgreSQL](https://www.postgresql.org/) for reading the cluster state

## Configuration
The [environ library](https://github.com/weavejester/environ) is used and therefore several variables can be
set by specifying them as environment variable or as java system property. Possible variables are:

| java system properties        | environment variables         | defaults                             |
|-------------------------------|-------------------------------|--------------------------------------|
| bob-storage-url               | BOB_STORAGE_URL               | jdbc:postgresql://localhost:5432/bob |
| bob-storage-user              | BOB_STORAGE_USER              | bob                                  |
| bob-storage-password          | BOB_STORAGE_PASSWORD          | bob                                  |
| bob-queue-url                 | BOB_QUEUE_URL                 | amqp://localhost:5672                |
| bob-queue-user                | BOB_QUEUE_USER                | guest                                |
| bob-queue-password            | BOB_QUEUE_PASSWORD            | guest                                |
| bob-api-host                  | BOB_API_HOST                  | 0.0.0.0                              |
| bob-api-port                  | BOB_API_PORT                  | 7777                                 |
| bob-health-check-freq         | BOB_HEALTH_CHECK_FREQ         | 60000                                |
| bob-connection-retry-attempts | BOB_CONNECTION_RETRY_ATTEMPTS | 10                                   |
| bob-connection-retry-delay    | BOB_CONNECTION_RETRY_DELAY    | 2000                                 |

## Message Schemas

Same as [Runner](/runner) and [Entities](/entities)

## Building and Running

### Requirements, min versions, latest recommended.
- JDK 11+
- RabbitMQ 3.8+
- PostgreSQL 11+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)
- (Optional) A bob artifact store like [artifact-local](https://github.com/bob-cd/artifact-local)

### Using Docker to easily boot up a local cluster
- Install Docker 18+ and start it up
- Run `docker run -it --name bob-queue -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine` to run the latest management enabled RabbitMQ instance on port `5672` and the admin control on port `15672`. The default credentials are `guest:guest`.
- Run `docker run --rm -it --name bob-storage -p 5432:5432 -e POSTGRES_DB=bob -e POSTGRES_USER=bob -e POSTGRES_PASSWORD=bob postgres:alpine` to run the latest PostgreSQL instance on port `5432`.

### Ways of connecting APIServer to the cluster
- To build an uberjar run `clojure -X:depstar uberjar :jar apiserver.jar :aot true :main-class apiserver.main` to obtain an `apiserver.jar.jar`. Running `java -jar apiserver.jar` should connect to it all nicely.
- To run directly without building a JAR, run `clj -m apiserver_next.main` from this dir.

## Setting up the dev environment with the REPL
- This uses [Component](https://github.com/stuartsierra/component) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/apiserver/src/apiserver_next/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests
Run `make clean test` from this dir. (needs docker)

Note: For people using multi java version tooling like [jenv](https://www.jenv.be/), you may need to set the `JAVA_HOME` variable for the make:
`make -e "JAVA_HOME=<path to java home>" clean test`
