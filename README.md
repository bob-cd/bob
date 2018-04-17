[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.org/bob-cd/bob.svg?branch=master)](https://travis-ci.org/bob-cd/bob)
[![codecov](https://codecov.io/gh/bob-cd/bob/branch/master/graph/badge.svg)](https://codecov.io/gh/bob-cd/bob)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

# Bob the Builder

![](http://vignette2.wikia.nocookie.net/dreamlogos/images/8/8d/Btb1.png/revision/latest?cb=20150801085138)

This is what CI/CD should've been.

### More info [here](https://github.com/bob-cd/bob/blob/master/RATIONALE.md)

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

**Bob the Builder image is ©BBC**
