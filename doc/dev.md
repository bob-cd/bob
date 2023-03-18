# Building Bob

These are some of the recommended ways in which Bob could be developed and have worked out for people.

## Local setup

This is a mono-repo of the main projects [apiserver](../apiserver) and [runner](../runner). The common code for them lives in [common](../common).
See the respective READMEs to get an idea of what needs to be installed locally.

### Editor setup

This is a standard clojure deps.edn based project and has been successfully worked upon with all of the major editors.

The following are the recommended steps:

- Make sure the [clojure-lsp](https://clojure-lsp.io/) setup is done.
- If working on one of the projects, treat that as the root dir and start your REPL there.
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
quay.io/podman/stable:v4.4.2 \
podman system service -t 0 tcp://0.0.0.0:8080

```

### Running tests

Again, due to the clustered setup, tests are a bit complex. However care has been taken to abstract it all away using [Babashka tasks](https://book.babashka.org/#tasks).

If developing the runner for instance:

- Make sure either docker or podman is setup.
- Run `bb test` from that dir.
- This brings up all the rest of the cluster and runs the tests in that context and cleans up afterwards.
