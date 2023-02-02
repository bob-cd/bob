# Contributing

Bob follows the The **[Collective Code Construction Contract (C4)](https://rfc.zeromq.org/spec:42/C4/).**

## Code style guide

The majority of Bob is written in Clojure and follows the [community style guide](https://guide.clojure.style/).
There are a few forms in the project which are not a part of the style guide but are to be styled as follows:

1. `failjure.core/try-all`: Shall be styled as [`let`](https://clojuredocs.org/clojure.core/let).
1. `failjure.core/when-failed`: Shall be styled as [`fn`](https://clojuredocs.org/clojure.core/fn).

The recommended formatter is [cljfmt](https://github.com/weavejester/cljfmt) and the code formatting rules can be found in [.cljfmt.edn](/.cljfmt.edn)

## Use of Git
* Avoid plain `git merge`, and use rebasing instead, to avoid merge commits. This keeps the history much more readable for others to understand, review bisect and rollback.
* When merging patches with multiple commits, try to make each patch meaningful.For example, fixes to patches should be squashed into the original patch. Security issues and other serious issues should be avoided in intermediate patches – even if they are fixed in later patches.

That's all folks! And Happy contributing!!
