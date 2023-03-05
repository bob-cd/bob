# 6. Drop the entities service

Date: 2023-03-05

## Status

Accepted

## Context

The entities service was created to manage the CRUD of pipelines, resource-providers and artifact-stores. But in practice it had minimal code and the intentions of doing smarter CRUD has been done via XT's temporal facilities.
The amount of code and infra needed for a full service just to do this wasn't justified.

## Decision

Drop the service and move its functionality to the APIServer.

## Consequences

This reduces the cluster complexity and moving parts significantly improving maintenance and usage.
