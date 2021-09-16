# 4. Introduce a temporal, document database

Date: 2020-09-27

## Status

Accepted

## Context

Currently we are using a traditional RDBMS, PostgreSQL as the storage of all the CI/CD state.
The DB is the one and only state of the whole CI/CD cluster, storing all of the pipeline, resource, artifact definitions, runs, logs etc. For all of these, tracking historical changes is of utmost importance.

Using a traditional CRUD workflow with PostgeSQL raises the following issues:
- Analytics are really hard as the Storage and Querying is coupled and a heavy query slows the whole cluster down
- Central locking is a severe impediment to scale
- When a change occurs in the pipeline, resource provider or artifact store definition its quite difficult to track these changes across time for audit and rollback needs
- To debug failures we need to have extended historical logging and is difficult to trace back in an update-in place system
- The tabular structure comes in our way with rigidity and is not simple to introduce schema changes
- Need of elaborate migration strategies which is further complicated with distributed state

## Decision

Based on the above facts the following is decided:
- Use [XTDB](https://xtdb.com) as the temporal, document store for the following reasons:
    - Though being newer compared to [Datomic](https://www.datomic.com/) its free and open source and ready for production use
    - Has a quite unbundled design and uses a variety of storage backends and is transactor free
    - Is [bi-temporal](https://xtdb.com/articles/bitemporality.html) and thereby offering more systematic analytical queries across time
    - Has an HTTP interface for non JVM clients
- Use it with JDBC/PostgreSQL backend which is quite readily available and managed in all popular cloud providers
- Remove the CRUD way of doing things, expose the DB too via API for more powerful, direct analytical querying

## Consequences

This has an immediate impact on the way the Bob is written and deployed. Most of the heavy lifting is moved to the DB. All of the services are Crux nodes and thereby having their own optimized indices for querying. The most useful outcome for this is the ability to freeze Bob at a point in time and inspect minute details while the real cluster is running.
