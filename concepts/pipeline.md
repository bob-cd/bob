---
layout: default
title: Pipeline
parent: Architechture
nav_order: 1
description: "Pipelines"
permalink: /concepts/pipeline
---

# Pipeline

A Pipeline is the only unit of execution in Bob. A pipeline consists of the following:

## Image

Bob implements a pipeline as a series of steps carried out on a starting Docker image.
This image is used to set the context of the build and is used to get the prerequisites like
compilers, build tooling, deployment tooling etc.

This has to be a Docker image which has been uploaded to Docker Hub.

Example: `ubuntu:latest`

## A List of Steps

### Step

A Step is essentially a key-value pair consisting of the following keys:

- `cmd`: String, Required: This is the command that is to be executed.
This is generally a shell command and it's validity is determined by image used
and/or the preceding steps.

Example: `cargo build` in case the `rust` Docker image being used.

- `needs_resource`: String, Optional: This denotes that a [Resource](/bob/concepts/resource) should
be mounted before the `cmd` is executed. The resource generally denotes something that the
command will be needing to successfully run. The resource is referred by the name its defined
in the resources section of the pipeline.

Example:
```json
{"needs_resource": "app-source-code"}
```

- `produces_artifact`: Key-Value Pair, Optional: This denotes the step will produce an
[Artifact](/bob/concepts/artifact) if successfully executed. This consists of the following keys:
    - `path`: String, Required: This is the path relative to the command being executed
    where the expected artifact will be produced. Bob will stream the artifact to the
    registered artifact provider. The path must exist.
    - `name`: String, Required: This the unique name with which the artifact will be uploaded
    to the artifact provider.

Example:
```json
{
  "prodcues_artifact": {
    "path": "target/app.jar",
    "name": "app-jar"
  }
}
```

## Vars

Vars is a map of key-value pairs which denotes the environment variables that is to be available
to all the steps of the pipeline.

Example:
```json
{
  "user": "wendy",
  "env": "prod"
}
```

## Resources

Resources is a list of key-value pairs which defines the list of Resources which may be
consumed by one or more of the steps of the pipeline.

Each entry consists of the following keys:
- `name`: String, Required: The unique name of the resource by which its to be referred in the
`needs_resource` key of a step.
- `type`: String, Required: This can either be `internal` or `external`. External resources are
to be fetched from a Resource Provider, whereas Internal ones are outputs of another pipeline in
the system. **Internal resources are not implemented yet.** See this [issue](https://github.com/bob-cd/bob/issues/42).
Resources are loaded lazily when required, so if a declared resource isn't used in a step,
it will not be fetched.

Conditional keys:

If type is `external`:
- `provider`: String, Required: This is the name of the Resource Provider which will provide this
resource when this step is about to be executed.
- `params`: Map[String, String], Required: This are the params that are to be sent to the Resource
Provider when requesting the resource. These are a property of that particular provider and helps
in customizing the kind of resource fetched.

If the type is `internal`: (Not implemented yet)
- `pipeline`: String, Required: This denotes the group/name of a pipeline in the system on the output
of which a Step depends. This is generally to be used to consume an artifact which has been produced
in another pipeline.
- `artifact_name`: String, Required: The name of the artifact that the other pipeline has produced
which should be mounted before Step execution.

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

The provider is a Resource Provider which to be registered before this pipeline is to be started.

Full working pipeline example:
```json
{
  "image": "busybox:musl",
  "vars": {
    "env": "test",
    "url": "test.com"
  },
  "steps": [
    {
      "cmd": "echo hello"
    },
    {
      "cmd": "sleep 10"
    },
    {
      "cmd": "sh -c 'touch test.txt && echo $env >> test.txt'"
    },
    {
      "cmd": "cat test.txt",
      "produces_artifact": {
        "name": "afile",
        "path": "test.txt"
      }
    },
    {
      "needs_resource": "my-source",
      "cmd": "ls",
      "produces_artifact": {
        "name": "license-file",
        "path": "LICENSE"
      }
    }
  ],
  "resources": [
    {
      "name": "my-source",
      "type": "external",
      "provider": "git",
      "params": {
        "repo": "https://github.com/bob-cd/bob",
        "branch": "master"
      }
    },
    {
      "name": "another-source",
      "type": "external",
      "provider": "git",
      "params": {
        "repo": "https://github.com/lispyclouds/clj-docker-client",
        "branch": "master"
      }
    }
  ]
}
```
