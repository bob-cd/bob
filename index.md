---
layout: default
title: Home
nav_order: 1
description: "Bob is a modular, extensible and less opinionated CI/CD platform."
permalink: /
---

# Bob the Builder: This is what CI/CD should've been.

Born out of a personal frustration with most CI/CD offerings,
Bob is your go to _CI/CD platform rather than just another tool_.

Bob consists of a small, simple and opinionated core designed to be solid, robust and externally extensible.
Think Emacs, LISP Macros, Clojure core etc.
Bob tries to exert the minimum amount of opinions on the environment its operating in.
As a _low-level_ building block, it allows users to _build_ their
own CI/CD platform. Think of Bob as a **CI Engine**

Want pipelines as code? Scale automatically to handle large build loads?
Want to_teach_ Bob something it can't do yet? Just talk to Bob's REST API.
Bob makes CI/CD [easy **and simple**](https://www.infoq.com/presentations/Simple-Made-Easy/).

## 🚧 This is still in its pre-alpha stages. 🚧

## Quick Start with Docker
```bash
# Clone the source repo:
git clone https://github.com/bob-cd/bob

# Use docker-compose to bring up the cluster
# The --privileged flag is necessary as Bob itself uses Docker internally
cd bob && docker-compose up bob

# ...loads of output...
# wait for Bob's listening on http://0.0.0.0:7777/
```

Use a reference CLI such as [Wendy](https://github.com/bob-cd/wendy) or your 
your own as Bob talks over plain REST and off you go!

### Bob can be found on [Github](https://github.com/bob-cd/bob) and is fully GPLv3+ licensed FOSS and any and every contribution is much appreciated!
