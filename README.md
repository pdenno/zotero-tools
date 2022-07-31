# zotero-tools
An example to explore [Clojurescript](https://clojurescript.org/),
[shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html),
[Datascript](https://github.com/tonsky/datascript),
[Zotero](https://www.zotero.org/) and maybe
[FulcroRAD](https://book.fulcrologic.com/) simultaneously.

# Installing

 Install shadow-cljs globally:
  `sudo npm install -g shadow-cljs`

 In the project directory:
  1. `npm init`
  2. `npm install zotero-api-client`
  3. `npm install shadow-cljs`

# Building/running


<!-- ## The script -->
<!--    The current script does nothing much, but the plan is to generate EDN or JSON corresponding to a Zotero Library. -->

<!-- # At a shell prompt: -->
<!-- # ```bash -->
<!-- #  shadow-cljs compile script                # This creates target/script.js (see ./shadow-cljs.edn, :target :script) -->
<!-- #  node --trace-warnings target/script.js    # Run the script. (--tracew-warnings is optional.)-->
<!-- # ```-->

## The server (in emacs, for development)

The "server" is currently just a minimal http server process that continues to run so that you can hot-load code from the REPL.
I use emacs/cider. Thus I'm starting a REPL from a shell and connecting to it through NREPL.

At a shell prompt in the project directory run the following to compile and start the NREPL process:
```bash
  shadow-cljs -d nrepl/nrepl:0.9.0 -d cider/piggieback:0.5.3 -d cider/cider-nrepl:0.28.5 watch server
```

The response should include *shadow-cljs - nREPL server started on port <some-port-number>* which is the port
you will use to connect the CIDER REPL to running application.
But first, in another shell, you need to start the server.
So in the second shell, in the project directy, run `node ./target/server.js.
The program should respond with:

```bash
starting server
http server running
shadow-cljs - #3 ready!
```

If you do the above, then when you try to interact with code in a CIDER REPL, it will report **No available JS runtime.**
If at anytime you see that warning, you can restart the server with `node target/script.js`.

Now you can connect with CIDER using `M-x cider-connect-cljs` and the port number observer earlier.
Respond with *shadow* as the REPL type and *:server* as the target compilation.

Note also that shadow-clsj implements a handy tool for inspection and compilation at http://localhost:9630/.
