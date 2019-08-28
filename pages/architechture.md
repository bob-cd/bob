---
layout: default
title: Architecture
nav_order: 3
has_children: true
has_toc: false
description: "Architecture"
permalink: /architecture
---

# Bob's Internals

Bob strides to be as small and simple as possible and allow itself to be extended externally via REST.

Currently on a high level Bob looks like this:

<img src="./diagrams/bob-arch.png">

## Bob Core

Bob follows the UNIX [Philosophy](https://en.wikipedia.org/wiki/Unix_philosophy)
of being small, robust and do one thing and do it well. Unlike most of the popular
CI tooling, Bob tries to be a collection of small components which not only does
one thing really well but allows you to _compose them in the way you want_
and build your CI platform.

### Implementation details

The core of the project live in this Github [repository](https://github.com/bob-cd/bob). Its written completely in [Clojure](https://clojure.org/) and runs on the JVM. Writing it in a functional language like Clojure allows Bob to be really small, scale well and handle concurrency well. It uses [PostgreSQL](https://www.postgresql.org/) as its database.

Bob's primary goal is to be small, simple, robust and _externally extensible_. Think Emacs, LISP Macros, Clojure core etc. Bob tries to exert as least amount of opinions as possible on the environment its operating in. This is the stark difference for Bob compared to other CIs as there is a certain set of opinions and quite often the need for plugins to make the core do the thing that you want.

_ALL_ of the internals is exposed via a standard REST API.

#### Pipeline

A [Pipeline](./concepts/pipeline) is the one and only build unit for Bob. Bob tries to be simple and avoids the need of complex hierarchies like Jobs. A pipeline is completely self contained and can be linked to other pipelines either in up/downstream.

#### Extending Bob and Plugins

Bob strongly _rejects_ the idea of traditional plugins wherein the plugin is generally written in the same technologies as the core and is loaded into the _same process_ as the core. Well known examples for this can be seen in [Jenkins](https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial), [GoCD](https://developer.gocd.org/current/writing_go_plugins/overview.html), [TeamCity](https://www.jetbrains.com/help/docs/teamcity/getting-started-with-plugin-development.html) and others. This style of extending the core functionality presents the following issues:
- The plugin author(s) must know the technologies used to develop the core.
- The core has now one more possible attack surface where an issue in the plugin can cause nasty things to happen in the core ranging from it being unstable to security [issues](https://www.cvedetails.com/vulnerability-list/vendor_id-15865/product_id-34004/Jenkins-Jenkins.html).
- Deploying these plugins often means a restart of the system, downtimes, misconfigurations and more importantly this needs happen on the same machine as the core and opens up a possibility of creating [snowflake](https://martinfowler.com/bliki/SnowflakeServer.html) servers.

To Bob, extending should be external, i.e. one should be able to _change Bob's behavior by calling/using it differently_ without having to dig too deep into the core.

Extension of a CI/CD system is generally needed in the case of making the system interact with the world differently. Examples would be cloning a different kind of source control, reading a file from network, reacting to events etc. For this Bob defines a [Resource](./concepts/resource), its way of abstracting out the need to know _how_ to fetch something.

The other need for extension is to store/deploy the results of a build somewhere. For this Bob defines an [Artifact](./concepts/artifact), its way of abstracting out the need to know _how_ to publish its build results.

Not only these things make it easy for Bob to be extended, things like UIs, CLI tooling, external orchestration to be completely 

#### The execution model

Like its depicted in the diagram above, Bob uses Docker internally as its _execution engine_. This provides an easily provisioned, isolated and disposable environment for build to take place in.

Bob has **no notion of workers or executors**. Instead Bob comprises of one or more equally able engines which are load balanced and all the state centrally stored out of the engines in the DB.

A pipeline is executed in the following way:
1. The image provided in the pipeline is taken as the starting point and is pulled by the docker daemon if already not present.
2. A container is created from this image with the command of the first step as the entry point.
3. If any environment variables are defined, they are added to the container.
4. If the step has defined a `needs_resource` key, the corresponding resource is fetched from the provider and copied over to the container.
5. The relevant working directory is set: the resource folder if a resource was mounted or the home of the container.
6. The container is started and Bob waits for its completion.
7. Bob attaches to the stderr and stdout of the container while its running and streams the log to the DB.
8. After the container exits, Bob checks if the exit code was zero, if yes and if a `produces_artifact` key was defined in the step, streams the artifact out from the path on the container to the Artifact Store. If the exit was non-zero, Bob marks the pipeline run as failed and stops executing the rest of the steps.
9. If the last step succeeded, Bob creates a diff of the current container which contains the effects of the last command via the [commit](https://docs.docker.com/engine/reference/commandline/commit/) feature. This becomes the next image in the series of execution of steps.
10. This recursively continues till there are no steps left. If all steps pass, Bob marks the pipeline run as passed.

The unit comprising of Bob core and the Docker daemon form a single deployment of Bob and arbitrary amounts of such units can be added behind a load balancer to easily scale Bob to thousands of concurrent builds. Check out the Kubernetes deployment [docs](./installation#deploying-on-an-actual-kubernetes-cluster) to be able to do this.
