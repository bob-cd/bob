# Building Bob

These are some of the recommended ways in which Bob could be developed and have worked out for people.

## Local setup

This is a mono-repo of the main projects [apiserver](../apiserver) and [runner](../runner). The common code for them lives in [common](../common).
See the respective READMEs to get an idea of what needs to be installed locally.

### Editor setup

This is a standard clojure deps.edn based project and has been successfully worked upon with all of the major editors.

The following are the recommended steps:

- Make sure the [clojure-lsp](https://clojure-lsp.io/) setup is done.
- If working on one of the projects, treat that as the root dir and start your REPL there using `bb repl`.
- Since this is a distributed system, its nice to have the necessary services around the ones you're working on be up and ready easiy:
  - Using the [docker-compose setup](https://github.com/bob-cd/bob-deploy/blob/main/docker-compose.yml) is a easy way.
  - If working on the apiserver, you can start the rest of the cluster with `docker-compose up storage queue artifact resource runner` and develop.
- Its also recommended to use this partial cluster setup to try running the service you just developed as well: `clojure -J--enable-preview -M -m apiserver.main`.

#### _Running_ the Runner

When developing the Runner, its a bit different as it needs Podman to be available and it looks for it on `http://localhost:8080` by default.
`CONTAINER_ENGINE_URL` can be set to change this.

As above, before starting up the Runner with `clojure -J--enable-preview -M -m runner.main`, make sure podman is running with:
```shell
docker run \
--rm \
--name podman \
--device /dev/fuse \
--security-opt seccomp=unconfined \
--security-opt apparmor=unconfined \
--security-opt label=disable \
--cap-add sys_admin \
--cap-add mknod \
-p 8080:8080 \
quay.io/podman/stable:v4.6.1 \
podman system service -t 0 tcp://0.0.0.0:8080

```

### Running tests

Again, due to the clustered setup, tests are a bit complex. However care has been taken to abstract it all away using [Babashka tasks](https://book.babashka.org/#tasks).

If developing the runner for instance:

- Make sure either docker or podman is setup.
- This brings up all the rest of the cluster and runs the tests in that context and cleans up afterwards.

Run tests:

```bash
$ bb test
```

Run a single test:

```bash
$ bb test --focus runner.pipeline-test/pipeline-starts
```

#### End to end (E2E) testing (or the lack of it)

The codebase used to have a full E2E suite which brought up a full cluster and performed all the operations from the API and asserted the effects.

However, in practice it was observed the tests were quite flaky and the amount of time spent in running them and the value they provided wasn't worth it. Hence the follwoing pattern is followed now:

- Have a shared set of [specs](https://github.com/bob-cd/bob/blob/main/common/src/common/schemas.clj) which:
  - Defines all the boudaries of each of the services.
  - Defines all the shape of the data in shared places like the DB and the Queue.
  - During normal operation, whenever a call to a shared place is made by any service, the data retreived is verified with the spec.
  - All the unit/intergration tests use these shared specs to verify the effects of calls into the shared places.
- This in effect, has the similar guarantees as the E2E tests apart from things like network/hardware failures.
- This is a much more consistent ahd reliable method to not only to run the cluster but to test it as well.
- When specs are stored and consumed centrally, it offers a much higher guarantee of maintainence and reliabilty of making a cross functional change.
