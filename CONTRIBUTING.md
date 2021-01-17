# Contributing

Bob follows the The **[Collective Code Construction Contract (C4)](https://rfc.zeromq.org/spec:42/C4/).**

## Code style guide

The majority of Bob is written in Clojure and follows the [community style guide](https://guide.clojure.style/).
There is a fair amount of Clojure forms in the project which are not a part of the style guide but are to be styled as follows:

1. `failjure.core/try-all`: Shall be styled as [`let`](https://clojuredocs.org/clojure.core/let) with the bindings aligned.
1. `failjure.core/when-failed`: Shall be styled as [`fn`](https://clojuredocs.org/clojure.core/fn).

The recommended linter is [zprint](https://github.com/kkinnear/zprint) and the code formatting rules can be found in [.zprintrc](https://github.com/bob-cd/bob/blob/main/.zprintrc)
For ease of use, its recommended to invoke zprint with the command line opt: `{:search-config? true}` so that it recursively searches upwards for a `.zprintrc`. This should work with any clojure file in the subdirectories.

Example:

To format `runner/src/runner/docker.clj` in place:
```bash
zprint '{:search-config? true}' -w runner/src/runner/docker.clj
```

## Use of Git
* Avoid plain `git merge`, and use rebasing instead, to avoid merge commits. This keeps the history much more readable for others to understand, review bisect and rollback.
* When merging patches with multiple commits, try to make each patch meaningful.For example, fixes to patches should be squashed into the original patch. Security issues and other serious issues should be avoided in intermediate patches – even if they are fixed in later patches.

That's all folks! And Happy contributing!!
