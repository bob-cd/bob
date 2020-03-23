# Bob the Builder [![](https://github.com/bob-cd/bob/workflows/Test-and-Publish/badge.svg)](https://github.com/bob-cd/bob/actions?query=workflow%3ATest-and-Publish)

[![License: AGPL v3+](https://img.shields.io/badge/license-AGPL%20v3%2B-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/messages/CPBAYJJF6)
[![Join the chat at https://gitter.im/bob-cd/bob](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bob-cd/bob)

## This is what CI/CD should've been.

### [Why](https://bob-cd.github.io/bob/why-bob) Bob

## 🚧 This is a proof of concept and isn't fully functional yet. 🚧
See the Kanban [board](https://github.com/bob-cd/bob/projects/1) to see the roadmap and planned work.

### Check out the [Getting Started](https://bob-cd.github.io/bob/getting_started) guide.

## Build requirements
- Any OS supporting Java and Docker
- JDK 8 to 12. 13+ will be supported when [#56](https://github.com/bob-cd/bob/issues/56) is fixed.
- [Boot](https://boot-clj.com/) 2.7+

## Running requirements
- Any OS supporting Java and Docker
- JDK 11+
- Docker (latest preferred for optimal performance)

## Configuration
Configuration uses the [environ library](https://github.com/weavejester/environ) and therefore several variables can be
set by specifying them as environment variable or as java system property. Possible variables are:
| java system properties | environment variables | defaults                    |
|------------------------|-----------------------|-----------------------------|
| bob-db-host            | BOB_DB_HOST           | localhost                   |
| bob-db-port            | BOB_DB_PORT           | 5432                        |
| bob-db-user            | BOB_DB_USER           | bob                         |
| bob-db-name            | BOB_DB_NAME           | bob                         |
| bob-docker-uri         | BOB_DOCKER_URI        | unix:///var/run/docker.sock |
| bob-connect-timeout    | BOB_CONNECT_TIMEOUT   | 1000ms                      |
| bob-read-timeout       | BOB_READ_TIMEOUT      | 30000ms                     |
| bob-write-timeout      | BOB_WRITE_TIMEOUT     | 30000ms                     |
| bob-call-timeout       | BOB_CALL_TIMEOUT      | 40000ms                     |

## Testing, building and running locally
- Clone this repository.
- Install the Build requirements.
- Following steps **need Docker**:
    - Run `boot kaocha` to run tests.
    - Start a PostgreSQL server instance locally on port 5432, and ensure a DB `bob` and a user `bob` exists on the DB.

      ```bash
        docker run --name bob-db             \
          -p 5432:5432                       \
          -e POSTGRES_DB=bob                 \
          -e POSTGRES_USER=bob               \
          -e POSTGRES_HOST_AUTH_METHOD=trust \
          -d postgres
      ```
    - Optionally if Resources and Artifacts are to be used follow the instuctions in the Resources [doc](https://bob-cd.github.io/bob/concepts/resource) and Artifacts [doc](https://bob-cd.github.io/bob/concepts/artifact) respectively.
    - Run `boot run` to start the server on port **7777**.

## Running integration tests:

**Docker is need to be installed for this**

One way to run the integration tests is to use docker. In the `integration-tests` dir, run:

`docker-compose up --abort-on-container-exit integration-tests`

*Note*: This will try to create new containers that might have been created by running `docker-compose up` in the source root. Hence you might need to clean up.

You can also run the tests using [strest](https://www.npmjs.com/package/@strest/cli).

- Start Bob either via boot or docker as mentioned above.
- Install strest
  ```
  npm i -g @strest/cli
  ```
- Run
  ```
  strest bob-tests.strest.yaml
  ```

*Note*: We're simulating a stateful client on the tests. Which means you'll have to reset the database between each run. (Drop the db docker container and restart it)

## For Cursive users:
This project is built using the Boot build tool which is unsupported on Cursive at the moment.

### To get it running on Cursive using leiningen:
- Install [Boot](https://boot-clj.com/) 2.7+.
- Install [Leiningen](https://leiningen.org/) 2.8+.
- Run `boot -d onetom/boot-lein-generate generate` to generate a `project.clj`.
- Open up this directory in Cursive and it should work.
- Happy development!

### Extensive Usage + API [docs](https://bob-cd.github.io/bob)

## Join the conversation

For discussions regarding the usage and general development of Bob join the Gitter [channel](https://gitter.im/bob-cd/bob).

For a more Clojure specific discussion we also have a Clojurians Slack [channel](https://clojurians.slack.com/messages/CPBAYJJF6).

You can come with us with any questions that seem too lengthy for github issues.

Happy Building!
