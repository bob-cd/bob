# 3. Introduce a unbundled, queue based architecture

Date: 2020-09-27

## Status

Accepted

## Context

The current design is based around a single Bob node written in Clojure responsible for:
- Exposing the REST API
- Implementing the step execution logic via a local Docker daemon
- Implementing the registration, creation and update of all the resources
- Controlling the lifecycle of a pipeline

This node is expected to be replicated based on the scaling needs and they all would be simply load balanced behind a simple Application Load Balancer like NGINX.

This brings forth the following issues:
- There is no back-pressure support: When the nodes are overwhelmed, there is no way to queue up builds and results in dropping of jobs and errors
- There is a shared state of where exactly the pipeline is really running and requests like stopping, pausing which need to be exactly delivered to the concerned node. There is no clean way of doing this
- The node being implemented in Clojure/JVM and using docker to implement the steps has a opinionated view:
    - Platforms which are either unsupported/resource constrained by the JVM cannot be addressed in a simple manner
    - Builds with special privileged needs aren't simple to implement
- There is no central place for errors and no ability to program/orchestrate on errors. Use case: CD for machine learning
- The scale bottle neck is the runner but the scale unit is the whole of Bob which is quite suboptimal
- It's not simple to implement a declarative style of CI/CD without queueing and back-pressure

## Decision

Based on the above facts the following is decided:
- Break the core up into 3 services:
    - API Server, implementing the spec-first API
    - Entities, implementing the creation, registration of entities like Pipeline, Resource Provider and Artifact Store
    - Runner, implementing the step execution logic based on Docker, streaming of logs to DB and pushing out the errors to the queue
- Use [RabbitMQ](https://www.rabbitmq.com/) as the central queue and rendezvous point for all the services for the following reasons:
    - It's quite ubiquitous and well battle tested
    - The protocol and client ecosystem is quite diverse and mature
    - It's quite resilient and independently scalable
- Use the fanout capabilities of the queue to broadcast the stop, pause requests to all connected runners

## Consequences

The queue brings forth the much needed back-pressure support that's crucial to all CI/CD tooling. This also enables the more independent and granular horizontal scaling of all the components. Apart from that, since the Runner is unbundled now, diverse runners with specialized abilities can be plugged in.
