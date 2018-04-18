[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.org/bob-cd/bob.svg?branch=master)](https://travis-ci.org/bob-cd/bob)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

# Bob the Builder

## This is what CI/CD should've been.

### More [info](https://github.com/bob-cd/bob/blob/master/RATIONALE.md)

## Build requirements
- Preferably any *nix environment
- JDK 1.8+
- [Leiningen](https://leiningen.org/) 2.0+

## Running requirements
- JRE 1.8+
- Docker

## Testing, building and running
- Run `lein cloverage` to run tests with coverage.
- Run `lein uberjar` to get the standalone JAR.
- Run `java -jar <JAR_file>` to start the server on port **7777**.

![](https://raw.githubusercontent.com/bob-cd/bob/master/resources/bob_cc.png)
