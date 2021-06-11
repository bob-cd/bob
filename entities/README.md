# Entities (please help us with a better name!)

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-611661635), this is the service that is responsible for performing the registration and de-registration of the core components of Bob, namely:
- [Pipeline](https://bob-cd.github.io/pages/concepts/pipeline.html)
- [Resource Provider](https://bob-cd.github.io/pages/concepts/pipeline.html)
- [Artifact Store](https://bob-cd.github.io/pages/concepts/artifact.html)

## How does this work
- This is implemented in Clojure/JVM
- Uses [RabbitMQ](https://www.rabbitmq.com/) to receive messages and perform the necessary effects
- Uses [Crux](https://www.opencrux.com/) backed by [PostgreSQL](https://www.postgresql.org/) for temporal persistence

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
| bob-connection-retry-attempts | BOB_CONNECTION_RETRY_ATTEMPTS | 10                                   |
| bob-connection-retry-delay    | BOB_CONNECTION_RETRY_DELAY    | 2000                                 |

The priority of your configuration is following:
1. Environment variables
1. Java system properties
1. Defaults

## Message Schemas

### Queue [Schema](/entities/Queue.md)
### Database [Schema](/entities/Db.md)

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

### Ways of connecting Entities to the cluster
- To build an uberjar run `clojure -X:depstar uberjar :jar entities.jar :aot true :main-class entities.main` to obtain an `entities.jar`. Running `java -jar entities.jar` should connect to it all nicely.
- To run directly without building a JAR, run `clj -M -m entities.main` from this dir.

## Setting up the dev environment with the REPL
- This uses [Component](https://github.com/stuartsierra/component) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](/entities/src/entities/system.clj). Eval this when there is change to reload the state cleanly.

### Running integration tests
Run `bb test` from this dir. (needs docker)
