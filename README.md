[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)
[![CircleCI](https://circleci.com/gh/bob-cd/bob/tree/master.svg?style=svg)](https://circleci.com/gh/bob-cd/bob/tree/master)
[![Dependencies Status](https://versions.deps.co/bob-cd/bob/status.png)](https://versions.deps.co/bob-cd/bob)

[![Built with Spacemacs](https://cdn.rawgit.com/syl20bnr/spacemacs/442d025779da2f62fc86c2082703697714db6514/assets/spacemacs-badge.svg)](http://spacemacs.org)
[![Join the chat at https://gitter.im/bob-cd/bob](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bob-cd/bob)

# Bob the Builder

## This is what CI/CD should've been.

### [Why](https://bob-cd.github.io/bob/why-bob) Bob

## 🚧 This is a proof of concept and isn't fully functional yet. 🚧
See the Kanban [board](https://github.com/bob-cd/bob/projects/1) to see the roadmap and planned work.

## Build requirements
- Any OS supporting Java and Docker
- JDK 8+ (latest preferred for optimal performance)
- [Boot](https://boot-clj.com/) 2.7+

## Running requirements
- Any OS supporting Java and Docker
- JRE 8+ (latest preferred for optimal performance)
- Docker (latest preferred for optimal performance)

## Testing, building and running locally
- Clone this repository.
- Install the Build requirements.
- Following steps **need Docker**:
    - Run `boot kaocha` to run tests.
    - Start a PostgreSQL server instance locally on port 5432, and ensure a DB `bob` and a user `bob` exists on the DB.

      ```bash
        docker run --name bob-db \
          -p 5432:5432 \
          -e POSTGRES_DB=bob \
          -e POSTGRES_USER=bob \
          -d postgres
      ```
    - Optionally if Resources and Artifacts are to be used follow the instuctions in the Resources [doc](https://bob-cd.github.io/bob/concepts/resource) and Artifacts [doc](https://bob-cd.github.io/bob/concepts/artifact) respectively.
    - Run `boot run` to start the server on port **7777**.

## Running integration tests:

**Docker may need to be installed for this**

One way to run the integration tests is to use docker. In the `integration-tests` dir, run:

`docker-compose up --abort-on-container-exit integration-tests`

*Note*: This will try to create new containers that might have been created by running `docker-compose up` in the source root. Hence you might need to clean up.

You can also run the tests using [strest](https://www.npmjs.com/package/@strest/cli).

- Start Bob either via boot or docker as mentioned above.
- Install strest
  ```
  npm i -g @strest/cli
  ```
- Run
  ```
  strest bob-tests.strest.yaml
  ```

*Note*: We're simulating a stateful client on the tests. Which means you'll have to reset the database between each run. (Drop the db docker container and restart it)

## Running Bob in Docker
Bob uses Docker as its engine to execute builds, but its now possible to run Bob
inside Docker using [dind](https://hub.docker.com/_/docker).

To use the provided docker-compose file, in the root dir of the project, run:

`docker-compose up bob`

This runs a single Bob instance forwarded on port `7777` along with a PostgreSQL server, the reference artifact store
and the resource provider.
Bob needs the `privileged` flag as it uses system Docker in Docker to function.
A reference CLI like [Wendy](https://github.com/bob-cd/wendy) may be used to talk to Bob.

## Running Bob on Kubernetes
To deploy Bob in Kubernetes, the necessary YAML files are provided in the `deploy` folder in the root of the project.

### Deploying locally on a [KinD](https://kind.sigs.k8s.io/) or [Minikube](https://kubernetes.io/docs/setup/learning-environment/minikube/) cluster:
1. Install [KinD](https://kind.sigs.k8s.io/docs/user/quick-start) or [Minikube](https://kubernetes.io/docs/setup/learning-environment/minikube/).
2. Run `kind create cluster --name bob` or `minikube start` to create a single node cluster for Bob.
   If using KinD, run `export KUBECONFIG="$(kind get kubeconfig-path --name="bob")"` to set the cluster context.
3. [Install](https://kubernetes.io/docs/tasks/tools/install-kubectl/) kubectl.
4. Run `kubectl apply -f deploy/psp.yaml` to apply the privileged security policies needed for Bob.
5. Run `kubectl apply -f deploy/db.yaml` to create a local PostgreSQL service.
6. [Optional] Run `kubectl apply -f deploy/artifact-local.yaml` to create the [reference artifact store](https://github.com/bob-cd/artifact-local).
   Alternatively a custom artifact store may also be used here.
7. [Optional] Run `kubectl apply -f deploy/resource-git.yaml` to create the [reference resource provider](https://github.com/bob-cd/resource-git).
   Alternatively a custom resource provider may also be used here.
8. Finally, run `kubectl apply -f deploy/bob.yaml` to create a 2 replica Bob cluster. The number of replicas
   can be altered in the spec/replicas section of the Deployment.
9. Run `kubectl port-forward svc/bob-lb 7777:7777` to forward Bob's load balancer on the 7777 host port and the
   cluster can be accessed via http://localhost:7777

### Deploying on an actual Kubernetes cluster

Its **STRONGLY RECOMMENDED** to run Bob on its own isolated cluster as it uses container privilege escalations for its functionality.

1. Setup an ideally multi-node Kubernetes cluster either On-Prem, or cloud or via an managed provider like
   Amazon [EKS](https://aws.amazon.com/eks/).
2. Follow the steps from 3 to 8 from the previous section. For step 5, its recommended to use a managed PostgreSQL
   provider like Amazon [RDS](https://aws.amazon.com/rds/). Change the environment values in the container spec of
   Bob's Deployment accordingly.
3. Bob will be available via its load balancer's public IP.

## For Cursive users:
This project is built using the Boot build tool which is unsupported on Cursive at the moment.

### To get it running on Cursive using leiningen:
- Install [Boot](https://boot-clj.com/) 2.7+.
- Install [Leiningen](https://leiningen.org/) 2.8+.
- Run `boot -d onetom/boot-lein-generate generate` to generate a `project.clj`.
- Open up this directory in Cursive and it should work.
- Happy development!

### Extensive Usage + API [docs](https://bob-cd.github.io/bob)

## Join the conversation

For discussions regarding the usage and general development of Bob join the Gitter [channel](https://gitter.im/bob-cd/bob).

For a more Clojure specific discussion we also have a [clojurians](http://clojurians.net/) Slack workspace head over [here](http://clojurians.net/). find our slack channel `#bob-cd`.

You can come with us with any questions that seem too lengthy for github issues.

Happy Coding!
