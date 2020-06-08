# Bob DB

Following the [diagram](https://github.com/bob-cd/bob/issues/70#issuecomment-640167633), this is the service that is responsible for providing a immutable datalog based temporal store for Bob.

## How does this work
- This is implemented in Clojure/JVM.
- Uses [Crux](https://opencrux.com/) backed by [PostgreSQL](https://www.postgresql.org/) to provide the bitemporal immutable store. PostgreSQL is chosen for easier setup and is readily available across as a managed service with most of the cloud providers.
- Exposes itself via REST for diverse clients to connect to it.

## Building and Running

### Requirements, min versions, latest recommended.
- JDK 11+
- PostgreSQL 11+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)

### Using Docker to easily boot up a local cluster
- Install Docker 18+ and start it up
- Build the image with `docker build -t bobcd/db .`
- Run a PostgreSQL container with `docker run -it --name bob-storage -p 5432:5432 -e POSTGRES_DB=bob -e POSTGRES_USER=bob -e POSTGRES_PASSWORD=bob postgres:alpine`
- Run the db container with `docker run -it --name db -p 7778:7778 -e BOB_STORAGE_HOST=host.docker.internal bobcd/db` The storage host here points to the postgres container running in another container.
- The HTTP server should be available on port `7778`. Follow the REST API [examples](https://opencrux.com/docs#restapi) and the transacting [samples](https://opencrux.com/docs#_transacting) to try it out.

## Setting up the dev environment with the REPL
- This uses [Component](https://github.com/stuartsierra/component) to manage state across the app.
- When loaded into the editor/REPL, find the `reset` fn in this [namespace](https://github.com/bob-cd/bob/blob/queue/entities/src/entities/system.clj). Eval this when there is change to reload the state cleanly.

#### Of course this is a MASSIVE work in progress!
