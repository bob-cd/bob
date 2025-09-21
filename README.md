# Bob the Builder

[![CircleCI](https://circleci.com/gh/bob-cd/bob/tree/main.svg?style=svg)](https://circleci.com/gh/bob-cd/bob/tree/main)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/messages/CPBAYJJF6)

> What [CI/CD](https://en.wikipedia.org/wiki/CI/CD) should've been.

Most CI/CD tools are too opinionated and do too much. Bob follows the UNIX philosophy of doing one thing and doing it well, and the Emacs/LISP like philosophy of small core with external extensibility, and strives for [simpler, decomposed and hence more composable and unbundled design](https://www.youtube.com/watch?v=MCZ3YgeEUPg). For more information, see [Why Bob](https://bob-cd.github.io/rationale/)

## Getting Started

To build and run your pipelines, check out the [Getting Started](https://bob-cd.github.io/getting-started/) guide.

## Overview

Bob's API (accessible entirely through HTTP) enables a core set of CI/CD features. The following are the only concepts that Bob is opinionated about:

- Step: Direct commands (like a shell command, `pytest`, etc)
- Pipeline: Ordered series of steps
- Environment: Key-Value pair associated with either Steps and/or Pipelines
- Resource: Things (like source code or artifacts) consumed by Pipelines
- Artifact: Something produced by a Pipeline
- Logs: The logs produced from a Pipeline run

The following services form the core cluster:

- [API server](/apiserver)
- [Runner](/runner)

All of these services live, breathe, and deploy from their own section of this mono-repo. Post-deployment, they are coordinated via a central persistent queue. Read more about Bob's [Architecture](https://bob-cd.github.io/architechture/).

## Join the conversation

Please start a [discussion](https://github.com/bob-cd/bob/discussions) on literally any topic and we are happy to help and learn from each other!

For a more Clojure specific discussion there we also have a Clojurians Slack [channel](https://clojurians.slack.com/messages/CPBAYJJF6).

Happy Building!

## License

Bob is [Free](https://www.gnu.org/philosophy/free-sw.en.html) and Open Source and always will be. Licensed fully under [MIT](https://opensource.org/licenses/MIT)
