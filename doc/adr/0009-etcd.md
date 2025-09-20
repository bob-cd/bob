# 9. etcd

Date: 2025-09-20

## Status

Accepted

## Context

Following [ADR #8](/doc/adr/0008-logger-interface.md), the logs are now stored externally and the only state of the cluster is its configuration which needs to be stored on DB.
XTDB 1 has served us well but its quite an overkill just to store config which hardly ever changes and we don't need OLAP queries. Additionally, XTDB v2's complex infra needs are a non-starter.
Hence, we need to consider a purpose built config store with the following attributes:

- is a KV store
- has revisions of keys, can time travel
- distributed, fault tolerant
- easy to setup infra

## Decision

[etcd](https://etcd.io) has been chosen for this for the following features:

- is a KV store
- has versioned keys
- designed for distributed use, Raft clustering
- very widely used, cloud native and powers k8s which could be leveraged

## Consequences

Bob now needs the following changes:

- Removal of the /query endpoint
- Changes in config to support etcd
