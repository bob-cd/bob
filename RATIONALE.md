### Some of the (non exhaustive) reasons of why Bob

I am primarily a server side dev dealing with a lot for DevOps tools almost everyday and specially CI servers. Some
of the pain points I see in systems like Jenkins and GoCD are:

- The plugin architecture. They are mainly to be extended via JARs which are loaded by the main process. This presents the following issues:
  - Bugs and vulnerabilities in the plugin. Most if not all of the plugins we use for these systems are the primary root cause for much of the CVEs. Also a badly written plugin can bring the whole thing down.
  - We absolutely have to use a JVM language to write the plugins.
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
  - Jobs (direct commands like shell)
  - Pipeline (Collection of Jobs which can consume artifacts from other pipelines)
  - Environment(Key value store associated with either Jobs and/or Stages and/or Pipelines)
  - Resources (Things like source code or assets)
  - Artifacts (Something built by a Pipeline)
- Expose the above feature set entirely via an API, hence allow any client to configure/control it. Also not be affected by bugs in it.
- Be agnostic of an UI
- Be more extensible than great things like Concourse.ci
- Accept the above stuff via an YAML as well
- Build pretty much everything else via plugins:
  - Have a set of core plugins (like Ansible)
  - Things like getting stuff from a VCS can be a plugin which can abstract away the type like Github or Bitbucket and do stuff like auth and finally give bob the direct resource URL.
  - Stuff like how to build an artifact like debs or RPMs can be plugins as well whose commands can be fed in as a task.
  - Plugins to automate the provisioning of a cloud instance or bare metal system to be able to execute tasks via SSH (Ansible way) and also clean up and when done.
  - Infra plugins like Terraform based stuff which can provision entire cloud infra to support bob and his agents.
- Scale via multiple servers and share loads and resources

And this is a project born out of my frustration and is VERY new. 😄

Any and every help, suggestion is most welcome!

#### A sample bob config:

```yaml
dev:
  pipelines:
    -
      test:
        image: clojure:latest
        auto: True
        resources:
          -
            plugin/github:
              branch: master
              src: https://github.com/bob-cd/bob.git
        steps:
          - "lein test"
    -
      build:
        image: clojure:latest
        artifacts:
          -
            name: jar
            path: ./target/bob*-standalone.jar
        auto: True
        env:
          password: test
          user: test
        steps:
          - "lein uberjar"
    -
      package:
        image: debian:latest
        artifacts:
          -
            name: dev_installer
            path: ./bob.zip
        auto: True
        resources:
          -
            pipeline/dev/build: jar
          -
            plugin/github:
              branch: master
              src: https://github.com/bob-cd/bob-conf.git
        steps:
          - "zip -r bob.zip ."
          -
            plugin/docker-image:
              dir: "."
stable:
  pipelines:
    -
      sign:
      image: debian:latest
        artifacts:
          -
            name: bob_signature
            path: ./bob.sig
        auto: True
        resources:
          -
            pipeline/dev/build: jar
        steps:
          - "gpg --output bob.sig --sign bob*-standalone.jar"
    -
      upload:
        image: debain:latest
        auto: False
        resources:
          -
            pipeline/dev/build: jar
          -
            pipeline/stable/sign: bob_signature
        steps:
          - "scp user@cdn.com *.sig"
          - "scp user@cdn.com *.jar"

```
