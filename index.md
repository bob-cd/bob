---
layout: default
title: Home
nav_order: 1
description: "Bob is a modular, extensible and less opinionated CI/CD platform."
permalink: /
---

# Bob the Builder: This is what CI/CD should've been.

Born out of a personal frustration of the usage of most of the CI/CD offerings,
Bob tries to be the least in your way and be a CI/CD platform rather than a tool
allowing you to construct the testing and delivery platform the way you want.

Bob significantly differs from the other established offerings in the fact that it
consists of a very small, simple and opinionated core designed to be solid, less
changing and extensible externally. **Bob has no notion of workers or plugins.**

Want pipelines as code? Deploy to Kubernetes? Scale automatically to handle large
build loads? _Teach_ Bob something it can't do yet? Just talk to Bob's REST API.
Bob lets you be in control of the whole process by allowing itself to be more
simple than easy.

## 🚧 This is a proof of concept and isn't ready for serious consumption yet! 🚧

## Quick Start with Docker
```bash
# Pull the latest built image
docker pull bobcd/bob

# Start a container locally on port 7777
# The --privileged flag is necessary as Bob itself uses Docker internally
docker run -it -p 7777:7777 --privileged --name bob bobcd/bob

# ...loads of output...
# wait for Bob's listening on http://0.0.0.0:7777/
```

Use a reference CLI like [Wendy](https://github.com/bob-cd/wendy) or something of
your own as Bob talks over plain REST and off you go!

### Bob is a fully GPLv3+ licensed FOSS and any and every contribution is much appreciated!

#### Choose one of the topics to your left to know more about Bob.
