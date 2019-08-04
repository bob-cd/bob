---
layout: default
title: Architecture
nav_order: 2
has_children: true
has_toc: false
description: "Architecture"
permalink: /architecture
---

# Bob's Internals

Bob strides to be as small and simple as possible and allow itself to be extended externally via REST.

Currently on a high level Bob looks like this:

<img src="./diagrams/bob-arch.png">

### Bob Core

The central part of Bob quite simple and small only concerning with:
- [Pipeline](./concepts/pipeline)
- [Resource](./concepts/resource)
- [Artifact](./concepts/artifact)

Bob follows the UNIX [Philosophy](https://en.wikipedia.org/wiki/Unix_philosophy)
of being small, robust and do one thing and do it well. Unlike most of the popular
CI tooling, Bob tries to be a collection of small components which not only does
one thing really well but allows you to _compose them in the way you want_
and build your CI platform.
