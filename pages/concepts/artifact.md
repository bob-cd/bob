---
layout: default
title: Artifact
parent: Architecture
nav_order: 3
description: "Artifact"
permalink: /concepts/artifact
---

# Artifact

An Artifact is the outcome of a pipeline which is to be stored away or consumed in another pipeline.

In Bob, an artifact can be produced at any Step in a pipeline. In Bob, like Concourse CI, its important
that artifacts **should not** be stored within the system as Artifacts should be there regardless of
Bob being there or not. This is something that is not there in most of the other on-prem CIs.

To denote that a step produces an artifact:

```json
{
  "cmd": "mvn build",
  "produces_artifact": {
    "name": "app-jar",
    "path": "target/app.jar"
  }
}
```

When Bob encounters such a step, it executes the step and if its successful, uploads the artifact
from the provided `path` to an Artifact Provider.

## Artifact Provider

An Artifact Provider is Bob's way of abstracting the kind of storage needed to store artifacts. This
is akin to an abstract object store where Bob can store and retrieve artifacts.

An artifact provider is any system which has the following properties:
- It is a web server.
- It is reachable from the network that Bob is in.
- Exposes an endpoint at `/bob_request/<path>` which:
  - When a `GET` request is made on it, the corresponding artifact is sent back.
  Here the `path` stands for a path to from which an artifact can be uniquely retrieved.
  This is like the key in Amazon's S3. Example: `bob_request/dev/test/1/test.jar`
  - When a `POST` request is made on it with the body containing the content in the `data` field,
  the data is saved at the `path`. Example:
  A POST request having the body:
  ```json
  {
    "data": <bytes from a file.>
  }
  ```
  - When a `DELETE` request is made on it, the corresponding resource is deleted at the `path`.

A reference artifact store which implements storage using local file system can be [found](https://github.com/bob-cd/artifact-local)

This gives the following advantages:
- Bob now doesn't have to care about _how_ to store an artifact.
- Using an artifact store details like auth, permissions etc can be offloaded outside of Bob.
- Multiple instances of Bob can share an Artifact store.
- Artifacts are preserved regardless of Bob's availability.
- The Artifact store can be written an any language and be registered at runtime with Bob.

An artifact store **must** be registered with Bob prior to the execution of a Step that produces an artifact.

To register an Artifact store with Bob:
- Make a `POST` request on the end point `/api/artifact-stores/<name>` with the body:
```json
{
  "url": "https://my-awesome-artifacts.bob.io"
}
```
- A `200` response from Bob indicates success.
Here <name> is the unique name with which Bob identifies this. The url must be reachable from Bob.

Conversely a `DELETE` request on `/api/artifact-stores/<name>` un-registers it from Bob.

To list the registered store make a `GET` request on `/api/artifact-stores`.

To retrieve an artifact from a pipeline run:
- Make a `GET` request on `/api/pipelines/groups/<group-name>/names/<pipeline-name>/number/<run-number>/artifacts/<artifact-name>`.
- The artifact is directly streamed from the Artifact Store via Bob.
