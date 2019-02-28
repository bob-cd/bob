## Some of the (non exhaustive) reasons of why Bob

I am primarily a server side dev dealing with a lot for DevOps tools almost everyday and specially CI servers. Some
of the pain points I see in systems like Jenkins and GoCD are:

- The plugin architecture. They are mainly to be extended via JARs which are loaded by the main process. This presents the following issues:
  - Bugs and vulnerabilities in the plugin. Most if not all of the plugins we use for these systems are the primary root cause for much of the CVEs. Also a badly written plugin can bring the whole thing down.
  - We absolutely have to use a JVM language to write the plugins if the CI is in Java.
  - We are limited by the API provided by the main system to do stuff like get resources and orchestrate the artifacts around.
- Monolithic, large and complex. IMO these systems have grown to a stage where they have a complex UX and requires a good amount of learning curve to use properly.
- The UI is merged with the back-end source hence pretty much providing an opinionated view of the CI infra and to control it. Building mobile interfaces or accessing on them can be tricky.
- The API is patchy. They don't really expose the entire infra via API and extending/controlling them via just their API is hard.
- Jenkins doesn't even have a proper notion of Pipeline and the flow of artifacts.
- Some good ones aren't even FOSS.
- Infra is hard to version control

Hence bob is what I was thinking. This is a **VERY** new project and haven't really thought it through. Here are the
features what I think it should have:
- Use GPL and be FOSS
- Have a very small core with a limited feature set. And be opinionated ONLY about them.
  - Steps (direct commands like shell)
  - Pipeline (Collection of steps which can consume artifacts from other pipelines)
  - Environment (Key value store associated with either steps and/or Pipelines)
  - Resources (Things like source code or artifacts produced by pipelines)
  - Artifacts (Something built by a Pipeline)
- Expose the above feature set entirely via an API, hence allow any client to configure/control it. Also not be affected by bugs in it.
- Be agnostic of an UI
- Be more extensible than great things like Concourse.ci
- Accept the above stuff via an YAML as well
- Build pretty much everything else external resources or orchestrate via API
- Scale via multiple federated bob instances(think Cassandra) and share loads and resources

And this is a project born out of my frustration and is VERY new. 😄

Any and every help, suggestion is most welcome!

#### A sample bob config:

```yaml
dev: # Group name
  pipelines:
    -
      test: # Pipeline name
        image: 'clojure:latest'
        resources:
          -
            name: my-source-code
            type: external
            provider: git # See https://github.com/bob-cd/resource-git
            params:
              branch: master
              url: 'https://github.com/bob-cd/bob.git'
        steps:
          -
            in_parallel:
              -
                needs_resource: my-source-code # Gets pulled for the first time.
                cmd: 'lein test :unit'
              -
                needs_resource: my-source-code # Cached
                cmd: 'lein test :integration'
    -
      build_and_push:
        image: 'clojure:latest'
        auto: true
        env:
          password: test
          user: test
        resources:
          -
            name: my-source-code
            type: external
            provider: git
            params:
              branch: master
              url: 'https://github.com/bob-cd/bob.git'
        artifacts:
          -
            name: jar
            path: target/bob-standalone.jar
        steps:
          -
            in_parallel:
              -
                needs_resource: my-source-code
                cmd: 'lein uberjar'
              -
                needs_resource: my-source-code
                cmd: 'lein deploy clojars'
    -
      package:
        image: 'debian:latest'
        auto: true
        resources:
          -
            name: my-jar
            type: artifact
            provider: 'dev:build_and_push'
        artifacts:
          -
            name: dev_installer
            path: bob.deb
        steps:
          -
            needs_resource: my-jar
            cmd: 'make deb-package'
stable:
  pipelines:
    -
      sign:
        image: 'debian:latest'
        artifacts:
          -
            name: my-signature
            path: bob.sig
        resources:
          -
            name: my-jar
            type: artifact
            provider: 'dev:build_and_push'
        steps:
          -
            needs_resource: my-jar
            cmd: 'gpg --output bob.sig --sign bob-standalone.jar'
    -
      deploy:
        image: 'ansible:latest'
        auto: false
        resources:
          -
            name: my-jar
            type: artifact
            provider: build_and_push
          -
            name: my-signature
            type: artifact
            provider: 'stable:sign'
        steps:
          -
            in_parallel:
              -
                needs_resource: my-signature
                cmd: 'scp user@cdn.com *.sig'
              -
                needs_resource: my-jar
                cmd: 'scp user@cdn.com *.jar'
          - 'ansible deploy prod' # Doesn't need a resource
```
