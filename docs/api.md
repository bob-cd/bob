## REST API Reference

Bob exposes a REST API which is self documented with [Swagger](https://swagger.io/).

The API docs and a simple testing client can be located on **http://localhost:7777/**

## Routes

**Version:** 0.1

### /api/pipeline/{group}/{name}
---
##### ***POST***
**Summary:** Creates a new pipeline in a group with the specified name.
                   Takes list of steps, the base docker image, a list of environment vars
                   and a list of artifacts generated from pipeline as POST body.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| Pipeline | body |  | Yes | [Pipeline](#pipeline) |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

##### ***DELETE***
**Summary:** Deletes a pipeline in a group with the specified name.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/pipeline/start/{group}/{name}
---
##### ***POST***
**Summary:** Starts a pipeline in a group with the specified name.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/pipeline/stop/{group}/{name}/{number}
---
##### ***POST***
**Summary:** Stops a pipeline run in a group with the specified name and number.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/pipeline/logs/{group}/{name}/{number}/{offset}/{lines}
---
##### ***GET***
**Summary:** Fetches logs for a pipeline run in a group with the specified
                    name, number, starting offset and the number of lines.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |
| offset | path |  | Yes | long |
| lines | path |  | Yes | long |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [LogsResponse](#logsresponse) |

### /api/pipeline/status/{group}/{name}/{number}
---
##### ***GET***
**Summary:** Fetches the status of pipeline run in a group with the specified name and number.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| group | path |  | Yes | string |
| name | path |  | Yes | string |
| number | path |  | Yes | long |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [StatusResponse](#statusresponse) |

### /api/pipeline/status/running
---
##### ***GET***
**Summary:** Returns list of the running pipeline names.

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [RunningResponse](#runningresponse) |

### /api/plugin/register/{name}
---
##### ***POST***
**Summary:** Registers a new plugin with a unique name and its attributes.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| name | path |  | Yes | string |
| PluginAttributes | body |  | Yes | [PluginAttributes](#pluginattributes) |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/plugin/unregister/{name}
---
##### ***POST***
**Summary:** Un-registers a new plugin with a unique name and URL.

**Parameters**

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| name | path |  | Yes | string |

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/plugins
---
##### ***GET***
**Summary:** Lists all registered plugins by name.

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [PluginResponse](#pluginresponse) |

### /api/can-we-build-it
---
##### ***GET***
**Summary:** Runs health checks for Bob.

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/gc
---
##### ***POST***
**Summary:** Runs the garbage collection for Bob, reclaiming resources.

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### /api/gc/all
---
##### ***POST***
**Summary:** Runs the full garbage collection for Bob, reclaiming all resources.

**Responses**

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 |  | [SimpleResponse](#simpleresponse) |

### Models
---

### LogsResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | [ string ] |  | Yes |

### Pipeline

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| steps | [ string ] |  | Yes |
| image | string |  | Yes |
| vars | [PipelineVars](#pipelinevars) |  | Yes |
| artifacts | [PipelineArtifacts](#pipelineartifacts) |  | Yes |

### PipelineArtifacts

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| PipelineArtifacts | object |  |  |

### PipelineVars

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| PipelineVars | object |  |  |

### PluginAttributes

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| url | string |  | Yes |

### PluginResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | [ string ] |  | Yes |

### RunningResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | [ [RunningResponseMessage](#runningresponsemessage) ] |  | Yes |

### RunningResponseMessage

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| group | string |  | Yes |
| name | string |  | Yes |

### SimpleResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | string |  | Yes |

### StatusResponse

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| message | string |  | Yes |
