---
layout: default
title: Home
nav_order: 1
description: "Bob is a modular, extensible and less opinionated CI/CD platform."
permalink: /
---

# Bob the Builder: This is what CI/CD should've been.

Born out of a personal frustration of the usage of most of the CI/CD offerings,
Bob tries to be the least in your way and be a _CI/CD platform rather than just a tool_.

Bob significantly differs from most of the other CI tooling in the fact that it
consists of a very small, simple and opinionated core designed to be solid, less
changing and extensible externally.

Want pipelines as code? Scale automatically to handle large build loads?
_Teach_ Bob something it can't do yet? Just talk to Bob's REST API.
Bob makes CI/CD [easy **and simple**](https://www.infoq.com/presentations/Simple-Made-Easy/).

Bob is intended to be a _low-level_ building block to allow users to _build_ their
own CI/CD platform. Think of Bob as a **CI Engine**

## 🚧 This is still in its pre-alpha stages. 🚧

## Quick Start with Docker
```bash
# Start a bob container locally on port 7777
# The --privileged flag is necessary as Bob itself uses Docker internally
docker run -it -p 7777:7777 --privileged --name bob bobcd/bob

# ...loads of output...
# wait for Bob's listening on http://0.0.0.0:7777/
```

Use a reference CLI like [Wendy](https://github.com/bob-cd/wendy) or something of
your own as Bob talks over plain REST and off you go!

### Bob is a fully GPLv3+ licensed FOSS and any and every contribution is much appreciated!
