---
layout: default
title: Installation
nav_order: 2
has_children: false
has_toc: false
description: "Installation"
permalink: /installation
---

# Installation


## Running Bob in [Docker](https://www.docker.com/)

Bob uses Docker as its engine to execute builds, but its now possible to run Bob
inside Docker using [DinD](https://hub.docker.com/_/docker).

To use the provided docker-compose file, clone the source and in the root dir of the project, run:

`docker-compose up bob`

This runs a single Bob instance forwarded on port `7777` along with a PostgreSQL server, the reference artifact store
and the resource provider.
_Bob needs the `privileged` flag as it uses system Docker in Docker to function._

A reference CLI like [Wendy](https://github.com/bob-cd/wendy) may be used to talk to Bob.

## Running Bob on [Kubernetes](https://kubernetes.io/)

To deploy Bob in Kubernetes, the necessary YAML files are provided in the [deploy](https://github.com/bob-cd/bob/tree/master/deploy) folder in the root of the project.

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


Due to Bob's distributed architecture, supporting of installation of Bob via a package manager is not a priority at the moment, but any help here would be much appreciated!