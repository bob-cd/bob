---
layout: default
title: Resource
parent: Architecture
nav_order: 1
description: "Resources"
permalink: /concepts/resource
---

# Resource

A Resource is a pre-requisite for a command in a Step.

Resource is a term used to describe an external requirement that is needed before a step
can be executed successfully, e.g. source code which needs to be continuously integrated and delivered.

To denote that a particular step needs a resource:

```json
{
  "cmd": "sbt test",
  "needs_resource": "my-scala-src"
}
```
This resource must be declared in the `resources` section of the Pipeline definition.

Each entry consists of the following keys:
- `name`: String, Required: The unique name of the resource by which its to be referred in the `needs_resource` key of a step.
- `type`: String, Required: This can either be `internal` or `external`. External resources are to be fetched from a Resource Provider, whereas Internal ones are outputs of another pipeline in the system (NOT IMPLEMENTED YET! See this [issue] (https://github.com/bob-cd/bob/issues/42). Resources are loaded lazily when required, so if a declared resource isn’t used in a step, it will not be fetched.

Conditional keys:

If type is external:
- `provider`: String, Required: This is the name of the Resource Provider which will provide this resource when this step is about to be executed.
- `params`: Map[String, String], Required: This are the params that are to be sent to the Resource Provider when requesting the resource. These are a property of that particular provider and helps in customizing the kind of resource fetched.

If the type is internal: (Not implemented yet)
- `pipeline`: String, Required: This denotes the group/name of a pipeline in the system on the output of which a Step depends. This is generally to be used to consume an artifact which has been produced in another pipeline.
- `artifact_name`: String, Required: The name of the artifact that the other pipeline has produced which should be mounted before Step execution.

Example:
```json
[
  {
    "name": "my-source",
    "type": "external",
    "provider": "github-provider",
    "params": {
      "repo": "https://github.com/bob-cd/bob",
      "branch": "master"
    }
  },
  {
    "name": "my-ml-model",
    "type": "internal",
    "pipeline": "dev/make-model",
    "artifact_name": "trained_model.json"
  }
]
```

A Resource can be used fetch _any_ external items which may be needed in a build.
This is provided to Bob via a Resource Provider.

## Resource Provider

A Resource Provider is Bob's way of abstracting itself from knowing about _how_ to fetch
various resources.

The most general use case for a resource provider is to clone source code that is to be
continuously integrated and delivered.

A Resource Provider is any system which has the following properties:
- It is a web server.
- It is reachable from the network that Bob is in.
- Exposes an endpoint at `/bob_request` upon which when a `GET` request is made,
  a `zip` file is sent back. The rationale for using the zip format is:
      - Its quite ubiquitous and can be implemented with relative ease.
      - Generally resources tend to be source code and have multiple files/folders and
        using an archive makes it easy to send multiple things.

A reference resource provider which handles simple Github pulls can be [found](https://github.com/bob-cd/resource-git)

This gives the following advantages:
- Bob doesn't have to care about _how_ to fetch a particular Resource nor does it have to care about _what_
  the resource is.
- Using a resource provider details like auth, permissions and in cases like source control, things pertaining
  to private repositories, user access etc can be offloaded outside of Bob.
- Multiple instances of Bob can share a resource provider.
- Pretty much any source of data can be abstracted specially the various VCSes.
- The Resource Provider can be written in any language, can scale independently of Bob and be
  registered at runtime with Bob.

A resource provider **must** be registered with Bob prior to the execution of a Step that needs a resource.

To register a Resource provider with Bob:
- Make a `POST` request on the end point `/api/external-resources/<name>` with the body:
```json
{
  "url": "https://my-awesome-resources.bob.io"
}
```
- A `200` response from Bob indicates success.
Here <name> is the unique name with which Bob identifies this. The url must be reachable from Bob.

Conversely a `DELETE` request on `/api/external-resources/<name>` un-registers it from Bob.

To list registered resource providers make a `GET` request on `/api/external-resources`.
