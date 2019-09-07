---
layout: default
title: REST API
nav_order: 4
permalink: /api
---

# Bob the Builder
The modular, extensible CI/CD platform.

## Version: 0.1

### /api/pipelines/groups/{group}/names/{name}

#### POST
##### Summary:

Creates a new pipeline in a group with the specified name.
                   Takes list of steps, the base docker image, a list of environment vars
                   and a list of artifacts generated from pipeline as POST body.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| Pipeline | body |  | Yes | [Pipeline](#pipeline) |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

#### DELETE
##### Summary:

Deletes a pipeline in a group with the specified name.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/pipelines/start/groups/{group}/names/{name}

#### POST
##### Summary:

Starts a pipeline in a group with the specified name.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/pipelines/stop/groups/{group}/names/{name}/number/{number}

#### POST
##### Summary:

Stops a pipeline run in a group with the specified name and number.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/pipelines/logs/groups/{group}/names/{name}/number/{number}/offset/{offset}/lines/{lines}

#### GET
##### Summary:

Fetches logs for a pipeline run in a group with the specified
                    name, number, starting offset and the number of lines.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |
| offset | path |  | Yes | long |
| lines | path |  | Yes | long |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [LogsResponse](#logsresponse) |

### /api/pipelines/status/groups/{group}/names/{name}/number/{number}

#### GET
##### Summary:

Fetches the status of pipeline run in a group with the specified name and number.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [StatusResponse](#statusresponse) |

### /api/external-resources/{name}

#### POST
##### Summary:

Registers an external resource with a unique name and its attributes.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| name | path |  | Yes | string |
| ResourceAttributes | body |  | Yes | [ResourceAttributes](#resourceattributes) |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

#### DELETE
##### Summary:

Un-registers an external resource with a unique name.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| name | path |  | Yes | string |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/external-resources

#### GET
##### Summary:

Lists all registered external resources by name.

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [ResourceResponse](#resourceresponse) |

### /api/pipelines/groups/{group}/names/{name}/number/{number}/artifacts/{artifact-name}

#### GET
##### Summary:

Returns the artifact archive of a pipeline

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |
| artifact-name | path |  | Yes | string |

##### Responses

| Code | Description |
| ---- | ----------- |
| default |  |

### /api/artifact-stores/{name}

#### POST
##### Summary:

Registers an artifact store by a unique name and its URL.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| name | path |  | Yes | string |
| ArtifactStoreAttributes | body |  | Yes | [ArtifactStoreAttributes](#artifactstoreattributes) |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

#### DELETE
##### Summary:

Un-registers an external resource with a unique name.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| name | path |  | Yes | string |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/artifact-stores

#### GET
##### Summary:

Lists the registered artifact store.

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [ArtifactStoreResponse](#artifactstoreresponse) |

### /api/can-we-build-it

#### GET
##### Summary:

Runs health checks for Bob.

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### Models


#### Artifact

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| name | string |  | Yes |
| path | string |  | Yes |

#### ArtifactStoreAttributes

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| url | string |  | Yes |

#### ArtifactStoreResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | [ArtifactStoreResponseMessage](#artifactstoreresponsemessage) |  | Yes |

#### ArtifactStoreResponseMessage

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| name | string |  | Yes |
| url | string |  | Yes |

#### LogsResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | [ string ] |  | Yes |

#### Pipeline

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| steps | [ [Step](#step) ] |  | Yes |
| image | string |  | Yes |
| vars | [PipelineVars](#pipelinevars) |  | No |
| resources | [ [Resource](#resource) ] |  | No |

#### PipelineResourcesParams

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| PipelineResourcesParams | object |  |  |

#### PipelineVars

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| PipelineVars | object |  |  |

#### Resource

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| name | string |  | Yes |
| params | [PipelineResourcesParams](#pipelineresourcesparams) |  | Yes |
| type | string |  | Yes |
| provider | string |  | Yes |

#### ResourceAttributes

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| url | string |  | Yes |

#### ResourceResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | [ string ] |  | Yes |

#### SimpleResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | string |  | Yes |

#### StatusResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | string |  | Yes |

#### Step

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| cmd | string |  | Yes |
| needs_resource | string |  | No |
| produces_artifact | [Artifact](#artifact) |  | No |
