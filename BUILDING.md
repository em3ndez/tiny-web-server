# Build and Test of Tiny itself

## Table of Contents
- [Building with Maven](#building-with-maven)
- [Running Tests](#running-tests)
- [Generating Coverage Reports](#generating-coverage-reports)
- [Tiny's own test results](#tinys-own-test-results)
- [Project & Source Repository](#project--source-repository)
- [Contributions](#contributions)
- [My Dev Setup](#my-dev-setup)
- [TODO List](#todo-list)

## Building with Maven

To compile the project using Maven, simply run:

```bash
mvn clean compile
```

This will compile the source files and place the compiled classes in the `target/classes/` directory.

## Running Tests

To run the tests using Maven, execute:

```bash
mvn test
```

This command will compile the test classes, execute the test suite, and display the results.

## Generating Coverage Reports

To generate coverage reports using JaCoCo with Maven, execute:

```bash
mvn jacoco:prepare-agent test jacoco:report
```

This will generate an HTML report in the `target/site/jacoco` directory.

## Tiny's own test results

As mentioned, Cuppa-Framework is the tech used for testing, and it outputs spec-style success/failure like so:

``` 
  When additional composition can happen on a previously instantiated Tiny.WebServer
    ✓ Then both endpoints should be accessible via GET
  Given a Tiny web server with ConcreteExtensionToServerComposition
    When that concrete class is mounted within another path
      ✓ Then endPoints should be able to work relatively
  Given a started Tiny web server
    When additional composition happens
      ✓ Then illegal state errors should happen for new paths()
      ✓ Then illegal state errors should happen for new 'all' filters()
      ✓ Then illegal state errors should happen for new filters()
      ✓ Then illegal state errors should happen for new endPoints()
      ✓ Then illegal state errors should happen for new endPoints()
  Given a Tiny web server with composed paths
    ✓ Then it should respond correctly to requests at the composed endpoint
  Given a Tiny web server with a reusable composition
    ✓ Then it should respond correctly to requests at the first composed endpoint
    ✓ Then it should respond correctly to requests at the second composed endpoint
    ✓ Then it should respond correctly to requests at the third composed endpoint
  Config permutations
    ✓ should create default config
    ✓ should set web port
    ✓ should set web socket port
    ✓ should set web socket backlog
    ✓ should set host and web port
    ✓ should set web socket bind address
    ✓ should set socket timeout
  Given a Tiny web server with a chunked response endpoint
    ✓ Then it should return the response in chunks
  When endpoint and filters can depend on components
    ✓ Then it should not be able to bypass IoC
    ✓ Then it should be able to get dep to function
    ✓ Then it should not be able to depend on items outside request scope
  When passing attributes from filter to endpoint
    ✓ Then an attribute 'user' can be passed from filter to endPoint for authentication
    ✓ Then an attribute 'user' should not be passed from filter to endPoint for when inauthentic
  When applying filters
    ✓ Then a filter can conditionally allow access to an endpoint
    ✓ Then a filter can conditionally deny access to an endpoint
    ✓ Then an endpoint outside that conditionally filters isn't blocked
  When a server is started
    ✓ Then a method filter can't be added anymore
    ✓ Then a 'all' filter can't be added anymore
  When a filter is already added
    ✓ Then an identical filter path can't be added again
  When using Selenium to subscribe in a browser
    ✓ Then it should echo three messages plus -1 -2 -3 back to the client
  Given a Tiny web server with a path registered
    ✓ Then it should not be able to register the same path again
  Given a Tiny web server with a path registered
    ✓ Then it should not be able to register the same path again
  Given a Tiny web server with filters and an endpoint
    ✓ Then it should collect statistics for filters and endpoint
    ✓ Then it should collect statistics for missing endpoints
    ✓ Then it should not collect statistics filter that notionally match when the endPoint is a 404
  When additional composition can happen on a previously instantiated Tiny.WebServer
    ✓ Then both endpoints should be accessible via GET
  Given a Tiny web server with ConcreteExtensionToServerComposition
    When that concrete class is mounted within another path
      ✓ Then endPoints should be able to work relatively
  Given a started Tiny web server
    When additional composition happens
      ✓ Then illegal state errors should happen for new paths()
      ✓ Then illegal state errors should happen for new 'all' filters()
      ✓ Then illegal state errors should happen for new filters()
      ✓ Then illegal state errors should happen for new endPoints()
      ✓ Then illegal state errors should happen for new endPoints()
  Given a Tiny web server with an SSE endpoint
    ✓ Then a client should receive server-sent events
  When serving static files
    ✓ Then it should return 200 and serve a text file
    ✓ Then it should return 404 for non-existent files
    ✓ Then it should return 200 and serve a file from a subdirectory
    ✓ Then it should return 200 and serve a non-text file
  Given an inlined Cuppa application
    When the endpoint can extract parameters
      ✓ Then it should extract parameters correctly from the path
      ✓ Then it should return 404 when two parameters are provided for a one-parameter path
    When the endpoint can extract query parameters
      ✓ Then it should handle query parameters correctly
    When an application exception is thrown from an endpoint
      ✓ Then it should return 500 and an error message for a runtime exception
    When the endpoint has query-string parameters
      ✓ Then it should parse query parameters correctly
    When response headers are sent to the client
      ✓ Then it should set the custom header correctly
    When an exception is thrown from a filter
      ✓ Then it should return 500 and an error message for a runtime exception in a filter
    When testing static file serving
      ✓ Then it should serve a static file correctly
      ✓ Then it should return 404 for a non-existent static file
      ✓ Then it should prevent directory traversal attacks
    When accessing a nested path with parameters
      ✓ Then it should extract parameters correctly from the nested path
      ✓ Then it should return 404 for an incorrect nested path
    When accessing the Echoing GET endpoint
      ✓ Then it should return the user profile for Jimmy
      ✓ Then it should return the user profile for Thelma
    When a server is started
      ✓ Then a endpoint can't be added anymore
  When using standalone Tiny.WebSocketServer without Tiny.WebServer
    ✓ Then it should echo three messages plus -1 -2 -3 back to the client
  When using Tiny.WebSocketServer with Tiny.WebServer and a contrived webSocket endpoint
    ✓ Then it should echo three modified messages back to the client (twice)
    ✓ Then it should do a 404 equivalent for a missing path
  When using standalone Tiny.WebSocketServer without Tiny.WebServer
    ✓ Then it should echo three messages plus -1 -2 -3 back to the client
  When mismatching domains on SocketServer client lib
    ✓ Conversation is vetoed
  Given a mocked ExampleApp
    When accessing the Greeting GET endpoint
      ✓ Then it should invoke the ExampleApp foobar method

  69 passing
```

ChatGPT estimates the path coverage for the `Tiny` source to be around 90-95%.
It is difficult to say precisely as the test coverage with jacoco misses some of the Java-8 lambda paths.

It would be nice to use Cuppa to generate example code in markdown, too.
That would need to have the same Java source parsing fu of the javac compiler, and that may never happen.
An AI could copy tests into markdown documentation quickly, and repeatably, I guess.

## Project & Source Repository

The project is organized as follows:

- **`Tiny.java`**: The main source file containing the implementation of the Tiny Webserver and related classes. This "production code" has no dependencies outside the JDK.
- **`tests/`**: Contains tests for the Tiny web server using the Cuppa framework. Package is different to the Tiny production class in order to not accidentally take advantage of public/package/private visibility mistakes which can't neatly be tested for otherwise.
- **`BUILDING.md`**: This file, providing an overview and documentation of the development of Tiny.
- **`README.md`**: User-centric overview and documentation of the project.
- **`test_libs/`**: Directory containing dependencies required for running tests - built by curl scripts in this README
- **`target/classes/`**: Directory where compiled classes are stored.
- **`target/test-classes/`**: Directory where compiled test classes are stored.

Notes:

1. `target/` is what Maven would use, but we're not using Maven for this repo (we did to discover the dependency tree - a python3 script)
2. Both Java sources have packages. While it is conventional to have sources in a dir tree that represents the package, you don't have to

Stats about Tiny:

Source file `Tiny.java` has approximately 794 lines of consequential code, via:

``` 
# `cloc` counts lines of code
# don't count } on their own on a line, or }); or }};  See https://github.com/AlDanial/cloc/issues/865
cat Tiny.java | sed '/\w*}\w*/d' | sed '/\w*}];\w*/d' | sed '/\w*});\w*/d'| sed '/\w*\/\//d' > tmpfile.java
cloc tmpfile.java
rm tmpfile.java
```

The tests directory contains approximately twice as many lines of consequential code 

# Contributions

Pull requests accepted. If you don't want to grant me copyright, I'll add "Portions copyright, YOUR NAME (year)"

# My Dev Setup

I use Intellij-IDEA to edit this. With [aider.chat/](https://aider.chat/) running from a terminal (either IDEAs own terminal or Ubuntu's)

IDEA doesn't mine that Tiny.java is not in a com/paulhammant/tiny directory tree - it does the right thing at build or run moments (including run tests and breakpoints).  The command-line build fu via make and Makefile also doesn't mind that this does not have a Maven or Gradle directory structure. IDEA will use its own build-artictacts directories (classes and test-classes). Make will use target/classes and target/test-classes as Maven would have done.

Though Tiny does not have dependencies outside the JDK, the tests do - lots. Sadly there's no public directed graph of deps in maven-central, so we one-time use Maven (and Python to go get those deps and place them in a directory that has to be manually added to Intellij-IDEA). I made a tool for that some months ago - https://github.com/paul-hammant/mvn-dep-getter. that curl-pipe script takes list of key deps, invokes maven to download those (and transitive deps) to a named directory:

```bash 
curl -s https://raw.githubusercontent.com/paul-hammant/mvn-dep-getter/refs/heads/main/mvn-dep-getter.py | python3 - org.forgerock.cuppa:cuppa:1.7.0,org.hamcrest:hamcrest:3.0,com.squareup.okhttp3:okhttp:5.0.0-alpha.14,org.mockito:mockito-core:5.14.2,org.seleniumhq.selenium:selenium-java:4.26.0 test_libs
```

Just 5 top level deps, in our case.

Or just invoke `make get-test-deps`. 

You would  go into `File->Project_Structure...` menu and click `+` for "New Project Library"

I wish there were a big-ass public graph of deps of Java jars.

# TODO list

1. Properly merge websocket functionality into WebServer

Challenge: reflection access to HttpServer fundamentals may be needed to coax desired functionality 
out of it, when that's not possible ordinarily. I've made a number of attempt at this, but not succeeded.

2. Harden against denial-of-service attacks

3. Work in HTTPS and WSS support rather than just allude to it in a wiki doc
