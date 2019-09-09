---
layout: default
title: Architecture
nav_order: 3
has_children: true
has_toc: false
description: "Architecture"
permalink: /architecture
---

# Bob's Architecture

Current architecture (on a high level):

<img src="./diagrams/bob-arch.png">

## Bob Core

Bob follows the UNIX [Philosophy](https://en.wikipedia.org/wiki/Unix_philosophy)
of being small, robust and do one thing and do it well. Unlike most of the popular
CI tooling, Bob tries to be a collection of small components which does
one thing really well and allows you to _compose them in the way you want_
and build your CI platform.

The core of the project lives in this Github [repository](https://github.com/bob-cd/bob). Its entirely written in [Clojure](https://clojure.org/), which allows Bob to be really small, scale easily and handle concurrency well. It uses [PostgreSQL](https://www.postgresql.org/) as its database runs on the JVM.

_ALL_ of the internals is exposed via a standard REST API.

## Pipeline

A [Pipeline](./concepts/pipeline) is the one and only build unit for Bob. Bob is simple and therefore avoids the need of complex hierarchies (e.g. Jobs). A pipeline is completely self contained and can be linked to other pipelines either in up/downstream.

## Extending Bob and Plugins

Extending should be external, i.e. one should be able to _change Bob’s behavior by calling/using it differently_ without having to dig too deep into the core.

Extension of a CI/CD system is generally needed in the case of making the system interact with the world differently. Examples would be cloning a different kind of source control, reading a file from network, reacting to events etc. For this Bob defines a [Resource](./concepts/resource), its way of abstracting out the need to know _how_ to fetch something.

The other need for extension is to store/deploy the results of a build somewhere. For this Bob defines an [Artifact](./concepts/artifact), its way of abstracting out the need to know _how_ to publish its build results.

### Problems with Extending other CI toolings

Bob strongly _rejects_ the idea of traditional plugins wherein the plugin is generally written in the same technologies as the core and is loaded into the _same process_ as the core. Well known examples for this can be seen in [Jenkins](https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial), [GoCD](https://developer.gocd.org/current/writing_go_plugins/overview.html), [TeamCity](https://www.jetbrains.com/help/docs/teamcity/getting-started-with-plugin-development.html) and others. This style of extending the core functionality presents the following issues:
- The plugin author(s) must know the technologies used to develop the core.
- The core has now one more possible attack surface where an issue in the plugin can cause nasty things to happen in the core ranging from it being unstable to security [issues](https://www.cvedetails.com/vulnerability-list/vendor_id-15865/product_id-34004/Jenkins-Jenkins.html).
- Deploying these plugins often means a restart of the system, downtimes, misconfigurations and more importantly this needs happen on the same machine as the core and opens up a possibility of creating [snowflake](https://martinfowler.com/bliki/SnowflakeServer.html) servers.

## The Execution Model

Like its depicted in the diagram above, Bob uses Docker internally as its _execution engine_. This provides an easily provisioned, isolated and disposable environment for build to take place in.

Bob has **no notion of workers or executors**. Instead Bob comprises of one or more equally able engines which are load balanced and all the state centrally stored out of the engines in the DB.

A pipeline is executed in the following way:
1. The image provided in the pipeline is pulled by the docker daemon (if already not present).
2. A container is created with the command specified in the first step.
3. If any environment variables are defined, they are added to the container.
4. If the step has defined a `needs_resource` key, the corresponding resource is fetched from the provider and copied over to the container.
5. The relevant working directory is set: the resource folder if a resource was mounted or the home of the container.
6. The container is started and Bob waits for its completion.
7. Bob attaches to the stderr and stdout of the container while its running and streams the log to the DB.
8. If the container exists with code was zero and if a `produces_artifact` key was defined in the step, Bob streams the artifact out from the path on the container to the Artifact Store. If the exit was anything other than zero, Bob marks the pipeline run as failed and stops executing the rest of the steps.
9. If the last step succeeded, Bob creates a diff of the current container which contains the effects of the last command via the [commit](https://docs.docker.com/engine/reference/commandline/commit/) feature. This becomes the next image in the series of execution of steps.
10. This recursively continues until there are no steps left. If all steps pass, Bob marks the pipeline run as passed.

The unit comprising of Bob core and the Docker daemon form a single deployment of Bob and arbitrary amounts of such units can be added behind a load balancer to easily scale Bob to thousands of concurrent builds. Check out the Kubernetes deployment [docs](./installation#deploying-on-an-actual-kubernetes-cluster) to be able to do this.
