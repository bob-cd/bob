[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)
[![CircleCI](https://circleci.com/gh/bob-cd/bob/tree/master.svg?style=svg)](https://circleci.com/gh/bob-cd/bob/tree/master)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

[![Built with Spacemacs](https://cdn.rawgit.com/syl20bnr/spacemacs/442d025779da2f62fc86c2082703697714db6514/assets/spacemacs-badge.svg)](http://spacemacs.org)
[![Join the chat at https://gitter.im/bob-cd/bob](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bob-cd/bob)

# Bob the Builder

## This is what CI/CD should've been.

### [Why](https://bob-cd.github.io/bob/why-bob) Bob

## 🚧 This is a proof of concept and isn't fully functional yet. 🚧
See the Kanban [board](https://github.com/bob-cd/bob/projects/1) to see the roadmap and planned work.

## Build requirements
- Any OS supporting Java and Docker
- JDK 8+ (latest preferred for optimal performance)
- [Boot](https://boot-clj.com/) 2.7+

## Running requirements
- Any OS supporting Java and Docker
- JRE 8+ (latest preferred for optimal performance)
- Docker (latest preferred for optimal performance)

## Testing, building and running locally
- Clone this repository.
- Install the Build requirements.
- Following steps **need Docker**:
    - Run `boot kaocha` to run tests.
    - Start a PostgreSQL server instance locally on port 5432.
    - Ensure a DB `bob` and a user `bob` exists on the DB.
    - Optionally if Resources and Artifacts are to be used follow the instuctions in the Resources [doc](https://bob-cd.github.io/bob/concepts/resource) and Artifacts [doc](https://bob-cd.github.io/bob/concepts/artifact) respectively.
    - Run `boot run` to start the server on port **7777**.

## Running Bob in Docker
Bob uses Docker as its engine to execute builds, but its now possible to run Bob
inside Docker using [dind](https://hub.docker.com/_/docker).

To use the provided docker-compose file, in the root dir of the project, run:

`docker-compose up bob`

Bob will be up on the forwarded host port `7777` along with a PostgreSQL server, the reference artifact store and the resource provider.
Bob needs the `privileged` flag as it uses system Docker in Docker to function.
A reference CLI like [Wendy](https://github.com/bob-cd/wendy) may be used to talk to Bob.

## Running integration tests:

**Docker needs to be installed for this**

In the `integration-tests` dir, run:

`docker-compose up --abort-on-container-exit integration-tests`

## For Cursive users:
This project is built using the Boot build tool which is unsupported on Cursive at the moment.

### To get it running on Cursive using leiningen:
- Install [Boot](https://boot-clj.com/) 2.7+.
- Install [Leiningen](https://leiningen.org/) 2.8+.
- Run `boot -d onetom/boot-lein-generate generate` to generate a `project.clj`.
- Open up this directory in Cursive and it should work.
- Happy development!

### Extensive Usage + API [docs](https://bob-cd.github.io/bob)
