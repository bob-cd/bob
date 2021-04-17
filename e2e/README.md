# End to End Tests

Running end to end tests gives us confidence in the work we do elsewhere on bob or its dependencies.

## Running
From the project root, run:
```bash
make e2e
```

## Development

Because the e2e tests run using [Babashka](https://github.com/babashka/babashka), if you want to use your REPL while developing tests, you'll need to run a Babashka REPL:

```bash
bb --socket-repl 5555
```

and then from your editor, you can connect to a Socket REPL, if you're using [vim iced](https://liquidz.github.io/vim-iced/) you can use the command:

```
:IcedConnectSocketRepl 5555
```

and from then on, you should be able to evaluate individual tests or code as you're used to.

If you want to do anything involving side effects, you'll need to run:
```
docker-compose up
```
in the e2e folder to access bob

#### Note
Because this code is using Babashka, there are certain limitations about which namespaces can be imported. The [Babashka docs](https://book.babashka.org/#built-in-namespaces) enumerate the available namespaces to import.
