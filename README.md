# Bob the Builder [![](https://github.com/bob-cd/bob/workflows/Test-and-Publish/badge.svg)](https://github.com/bob-cd/bob/actions?query=workflow%3ATest-and-Publish)

[![License: AGPL v3+](https://img.shields.io/badge/license-AGPL%20v3%2B-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)

[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/messages/CPBAYJJF6)

## This is what [CI/CD](https://en.wikipedia.org/wiki/CI/CD) should've been.

### 🚧 This is a proof of concept and isn't fully functional yet. 🚧

### [Why](https://bob-cd.github.io/pages/why-bob.html) Bob

See the Kanban [board](https://github.com/bob-cd/bob/projects/1) to see the roadmap and planned work.

### Check out the [Getting Started](https://bob-cd.github.io/pages/getting-started.html) guide.

## Components

The core of Bob is an amalgamation of 3 main services:
- [API server](/apiserver)
- [Entities](/entities)
- [Runner](/runner)

Bob follows the idea of [simpler, decomposed and hence more composable and unbundled design](https://www.youtube.com/watch?v=MCZ3YgeEUPg).

All of these services live, breathe and deploy from their own section of this mono-repo and post-deployment, they are coordinated via a central persistent queue. Read more about Bob's [architecture](https://bob-cd.github.io/pages/architecture.html).

## Join the conversation

Please start a [discussion](https://github.com/bob-cd/bob/discussions) on literally any topic and we are happy to help and learn from each other!

For a more Clojure specific discussion we also have a Clojurians Slack [channel](https://clojurians.slack.com/messages/CPBAYJJF6).

Happy Building!

## License
Bob is [Free](https://www.gnu.org/philosophy/free-sw.en.html) and Open Source and always will be. Licensed fully under [GNU AGPLv3+](https://www.gnu.org/licenses/agpl-3.0)
