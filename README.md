[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.org/bob-cd/bob.svg?branch=master)](https://travis-ci.org/bob-cd/bob)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

# Bob the Builder

## This is what CI/CD should've been.

### More [info](https://github.com/bob-cd/bob/blob/master/RATIONALE.md)

## Build requirements
- Any OS supporting Java and Docker
- JDK 8+ (latest preferred for optimal performance)
- Clojure 1.9+
- [Leiningen](https://leiningen.org/) 2.0+

## Running requirements
- Any OS supporting Java and Docker
- JRE 8+ (latest preferred for optimal performance)
- Docker (latest preferred for optimal performance)

## Testing, building and running
- Clone this repository.
- Install the Build requirements.
- Following steps **need Docker**:
    - Run `lein test` to run tests.
    - Run `lein uberjar` to get the standalone JAR.
    - Run `java -jar ./target/bob-standalone.jar` to start the server on port **7777**.

![](https://raw.githubusercontent.com/bob-cd/bob/master/resources/bob_cc.png)
