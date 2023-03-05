# 5. Introduce a rootless, unprivileged runtime

Date: 2021-08-09

## Status

Accepted

## Context

Currently we are using [Docker](https://www.docker.com/) as the runtime and orchestration platform for implementing the pipeline steps.
Given its maturity, ubiquitous deployment and tooling around it, it gave us great ease to use it specially via its REST API.
Bob is what it is mainly due to the enablement Docker had.

However, it raises the following issues:
- Docker mainly runs as a daemon and moreover **needs root access** for the daemon function
- Bob is generally intended as a [Cloud Native](https://en.wikipedia.org/wiki/Cloud_native_computing) tool which means all components should be containerized.
  - Given that docker needs root permissions to run, the runner needs to be privileged to function, causing a security risk to the cluster
  - The alternative being to mount the host's docker socket into the runner container which is an even bigger security risk
- Docker running as a daemon in a container is not a very reliable setup and its own monitoring is still a concern

## Decision

Based on the above facts the following is decided:
- Use [Podman](https://podman.io/) as the container runtime and orchestration engine for the following reasons:
  - It is rootless and daemonless
  - Developed by [RedHat](https://www.redhat.com/) and the [OCI](https://opencontainers.org/) community
  - Fully FOSS
  - Exposes a REST API which is docker complaint too
  - Brings in the possibilities of more things like pods and more
- Swap out [clj-docker-client](https://github.com/into-docker/clj-docker-client) in favor of [contajners](https://github.com/lispyclouds/contajners) as the Clojure interface to the engine
- Have a self contained image having the runner, the JVM and Podman and run it **unprivileged**.

## Consequences

This will address the severe security concerns and allow the Bob runner to be truly self contained and cloud native. This makes it simpler to deploy and scale in modern cloud environments.
