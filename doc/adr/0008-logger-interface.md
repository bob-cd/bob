# 8. Logger interface

Date: 2025-08-16

## Status

Accepted

## Context

Logs like artifacts, are something that are the result of a run and hence should also be stored outside of the CI system.
Bob does not not that and to fetch logs, the cluster needs to be present and operational.
Additionally, logs are an immutable entity and can produce potentially lots of lines which could be huge.
Storing log lines in XTDB as individual items are an overkill in term of space and complexity.

## Decision

A logger interface like the resource_provider and artifact_store ones must be made supporting the following:

1. a HTTP API
1. `PUT /bob_logs/runs/<run-id>` endpoint taking in data to store, which is assumed to be a UTF-8 string appended to what's already there
1. `GET /bob_logs/runs/<run-id>` endpoint which starts to stream data til the client disconnects
1. `DELETE /bob_logs/runs/<run-id>` endpoint which deletes all data related to the run-id
1. `GET /ping` endpoint which supports ping based health checks from Bob

This would abstract out where and how the logs are stored, deferring the hard work to purpose built tooling like Loki, Elasticsearch etc.

A time based filter may be added later but deemed not important as of now as the run-id is paramount here.

A default reference service storing in simple files is to be provided as well.

## Consequences

Bob now needs the following changes:

- Runners now needs to support the logger interface, make calls to the logger API
- Remove the code to store the logs in XTDB
- Support the life cycle of a logger service by name and url and its health checks
- The apiserver needs to stream the logs from the service instead of XTDB
