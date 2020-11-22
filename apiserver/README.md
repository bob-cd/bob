# API Server

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-611661635), this is the service that is responsible for implementing the REST API for Bob.

This is intended as the coherent gateway for the Bob cluster having the schema checks, health checks for all the services and useful overviews like system status and metrics.

## How does this work
- This is implemented in plain Java with [Vert.x](https://vertx.io/)
- Implements a [spec-first](https://www.atlassian.com/blog/technology/spec-first-api-development) REST API with this OpenAPI 3.0+ [schema](/apiserver/src/main/resources/bob/api.yaml)
- Uses [RabbitMQ](https://www.rabbitmq.com/) to send the requests from the API
- Uses [Crux](https://www.opencrux.com/) backed by [PostgreSQL](https://www.postgresql.org/) for reading the cluster state

## Configuration
This can be configured with environment variables. Possible variables are:

| environment variables         | defaults                             |
|-------------------------------|--------------------------------------|
| BOB_STORAGE_URL               | jdbc:postgresql://localhost:5432/bob |
| BOB_STORAGE_USER              | bob                                  |
| BOB_STORAGE_PASSWORD          | bob                                  |
| BOB_QUEUE_URL                 | amqp://localhost:5672                |
| BOB_QUEUE_USER                | guest                                |
| BOB_QUEUE_PASSWORD            | guest                                |
| BOB_API_HOST                  | 0.0.0.0                              |
| BOB_API_PORT                  | 7777                                 |
| BOB_HEALTH_CHECK_FREQ         | 5000                                 |
| BOB_CONNECTION_RETRY_ATTEMPTS | 10                                   |
| BOB_CONNECTION_RETRY_DELAY    | 2000                                 |

## Message Schemas

Same as [Runner](/runner) and [Entities](/entities)

## Building and Running

### Requirements, min versions, latest recommended.
- JDK 15+
- RabbitMQ 3.8+
- PostgreSQL 11+
- [Gradle](https://gradle.org/)
- (Optional) A bob artifact store like [artifact-local](https://github.com/bob-cd/artifact-local)

### Using Docker to easily boot up a local cluster
- Install Docker 18+ and start it up
- Run `docker run -it --name bob-queue -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine` to run the latest management enabled RabbitMQ instance on port `5672` and the admin control on port `15672`. The default credentials are `guest:guest`.
- Run `docker run --rm -it --name bob-storage -p 5432:5432 -e POSTGRES_DB=bob -e POSTGRES_USER=bob -e POSTGRES_PASSWORD=bob postgres:alpine` to run the latest PostgreSQL instance on port `5432`.

### Ways of connecting the API Server to the cluster
- To build a jar run `gradle shadowJar` to obtain a `build/libs/apiserver.jar`. Running `java -jar apiserver.jar` should connect to it all nicely.
- To run directly without building a JAR, run `gradle run` from this dir.

### Running integration tests

Run `make clean test` from this dir. (needs docker)

Note: For people using multi java version tooling like [jenv](https://www.jenv.be/), you may need to set the `JAVA_HOME` variable for the make:
`make -e "JAVA_HOME=<path to java home>" clean test`
