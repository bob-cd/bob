# 2. Replace Compojure with spec-first solution

Date: 2020-04-19

## Status

Accepted

## Context

Compojure is a routing library that we are using right now. The downside is that one has to specify the routes
as code. We want an openapi-spec that defines our API and a router that creates routes from it. The specs should
be openapi 3 conform.

Besides that we need to model complex flows, an easy way to model auth per resource, easy swagger docs
and we want good async performance.

## Decision

Since there is no obvious Clojure implementation of a spec-first routing library and [juxt/apex](https://github.com/juxt/apex)
is in its infancy we are stuck with Java options. [openapi.tools](https://openapi.tools/#server) tells us there
is Spring and Vert.x so we chose to use [Vert.x](https://vertx.io/) for our new API-server.

## Consequences

Since there is already a spec written down in a yaml-file and the contract is already given we only have to
implement this contract. Since it is Java interop there will be coming some difficulties but thanks to
the groundwork of @lispyclouds we have a nice monadic prototype already running.
