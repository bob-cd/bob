# Runner

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-611661635), this is the service that is responsible for performing the **starting, stopping, pausing and streaming logs** of [Pipelines](https://bob-cd.github.io/pages/concepts/pipeline.html).

This provides a general enough and ephemeral execution environment via Docker. Each pipeline must begin with a starting image and the steps are applied in order on that image to reach the final state.

The logs are streamed live directly to the Database.

## How does this work
- This is implemented in Clojure/JVM
- Uses [RabbitMQ](https://www.rabbitmq.com/) to receive messages and perform the necessary effects
- Uses [Crux](https://www.opencrux.com/) backed by [PostgreSQL](https://www.postgresql.org/) for temporal persistence
- Uses [clj-docker-client](https://github.com/into-docker/clj-docker-client) to talk to the docker daemon to implement step executions.

## Configuration
The [environ library](https://github.com/weavejester/environ) is used and therefore several variables can be
set by specifying them as environment variable or as java system property. Possible variables are:

| java system properties        | environment variables         | defaults  |
|-------------------------------|-------------------------------|-----------|
| bob-storage-host              | BOB_STORAGE_HOST              | localhost |
| bob-storage-port              | BOB_STORAGE_PORT              | 5432      |
| bob-storage-user              | BOB_STORAGE_USER              | bob       |
| bob-storage-database          | BOB_STORAGE_DATABASE          | bob       |
| bob-storage-password          | BOB_STORAGE_PASSWORD          | bob       |
| bob-queue-host                | BOB_QUEUE_HOST                | localhost |
| bob-queue-port                | BOB_QUEUE_PORT                | 5672      |
| bob-queue-user                | BOB_QUEUE_USER                | guest     |
| bob-queue-password            | BOB_QUEUE_PASSWORD            | guest     |
| bob-connection-retry-attempts | BOB_CONNECTION_RETRY_ATTEMPTS | 10        |
| bob-connection-retry-delay    | BOB_CONNECTION_RETRY_DELAY    | 2000      |

The priority of your configuration is following:
1. Environment variables
1. Java system properties
1. Defaults

## Message Schemas

### Queue [Schema](/runner/Queue.md)
### Database [Schema](/runner/Db.md)

## Building and Running

### Requirements, min versions, latest recommended.
- JDK 11+
- RabbitMQ 3.8+
- PostgreSQL 11+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)

### Using Docker to easily boot up a local cluster
- Install Docker 18+ and start it up
- Run `docker run -it --name bob-queue -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine` to run the latest management enabled RabbitMQ instance on port `5672` and the admin control on port `15672`. The default credentials are `guest:guest`.
- Run `docker run --rm -it --name bob-storage -p 5432:5432 -e POSTGRES_DB=bob -e POSTGRES_USER=bob -e POSTGRES_PASSWORD=bob postgres:alpine` to run the latest PostgreSQL instance on port `5432`.

### Ways of connecting Runner to the cluster
- To build an uberjar run `clojure -Spom && clojure -A:uberjar` to obtain a `runner.jar`. Running `java -jar runner.jar` should connect to it all nicely.
- To run directly without building a JAR, run `clj -m runner.main` from this dir.

## Setting up the dev environment with the REPL
- This uses [Component](https://github.com/stuartsierra/component) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/runner/src/runner/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests
Run `make clean test` from this dir. (needs docker)

Note: For people using multi java version tooling like [jenv](https://www.jenv.be/), you may need to set the `JAVA_HOME` variable for the make:
`make -e "JAVA_HOME=<path to java home>" clean test`
