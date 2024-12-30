# Build and Test of Tiny itself

## Compiling Tiny

To compile `Tiny.java`, simply run:

```bash
make compile
```

This will compile the source file into the `target/classes/` directory.

## Tests

To compile and run the tests, including downloading necessary dependencies, use:

```bash
make tests
```

This command will handle downloading test dependencies, compiling the test classes, and executing the test suite.

## Coverage Reports

To generate coverage reports using JaCoCo, execute:

```bash
make coverage
make report
```

These commands will instrument the code for coverage, run the tests, and generate an HTML report in the `jacoco-report` directory.

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
Dec 21, 2024 3:28:48 PM org.openqa.selenium.devtools.CdpVersionFinder findNearestMatch
WARNING: Unable to find an exact match for CDP version 131, returning the closest version; found: 130; Please update to a Selenium version that supports CDP version 131
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
    ✓ Then it should receive server-sent events
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
- **`README.md`**: This file, providing an overview and documentation of the project.
- **`test_libs/`**: Directory containing dependencies required for running tests - built by curl scripts in this README
- **`target/classes/`**: Directory where compiled classes are stored.
- **`target/test-classes/`**: Directory where compiled test classes are stored.

Notes:

1. `target/` is what Maven would use, but we're not using Maven for this repo (we did to discover the dependency tree - a python3 script)
2. Both Java sources have packages. While it is conventional to have sources in a dir tree that represents the package, you don't have to

Stats about Tiny:

Source file `Tiny.java` has approximately 835 lines of consequential code, via:

``` 
# `cloc` counts lines of code
# don't count } on their own on a line, or }); or }};  See https://github.com/AlDanial/cloc/issues/865
cat Tiny.java | sed '/\w*}\w*/d' | sed '/\w*}];\w*/d' | sed '/\w*});\w*/d'| sed '/\w*\/\//d' > tmpfile.java
cloc tmpfile.java
rm tmpfile.java
```

The README is bigger. The tests are twice as big.

## Known Limitations

Mostly "Batteries not included" ...

* No template-engine bindings or examples or linkages to front-end techs (Angular/React/Vue),
* No integrations with event techs (Kafka, etc) or databases
* No Kotlin enablers or examples
* No GraalVM / native support
* No reactive examples
* No easy/automatic OpenAPI or Swagger
* No built-in HTTPS / WSS support, let alone LetsEncrypt cert fu
* No ram caching for things that don't change with lots of GET traffic
* No extensively performance or load tested. Expected to perform efficiently for small to medium-sized applications, but nor 10K class application serving.
* Doesn't have an async nature to request handling
* No opinion on user sessions
* Nothing built-in for event sourcing
* Theres a rudimentary way to configure keep-alibe and socket timeouts, but nothing sophisticated
* Utilizes some regex wrapping of Java's built-in webserver tech - either could have vulns versus Netty, etc.
* No Java Platform Module System (JPMS) participation
* No Maven-central publication - you could curl the single source file into your codebase if you wanted - see below

## Wiki

See [https://github.com/paul-hammant/tiny/wiki]

# Contributions & Published versions

Pull requests accepted. If you don't want to grant me copyright, I'll add "Portions copyright, YOUR NAME (year)"

**Before committing to main for an impending release, I will do the following, if I remember**

``` 
cat Tiny.java | sed '/SHA256_OF_SOURCE_LINES/d' > tmpfile.java
SHA=$(sha256sum tmpfile.java | cut -d ' ' -f1)
rm tmpfile.java
echo $SHA
sed "s/.*SHA256_OF_SOURCE_LINES.*/    public static final String SHA256_OF_SOURCE_LINES = \"$SHA\"; \/\/ this line not included in SHA256 calc/" -i Tiny.java
```

**Curl statements for you to copy, per release:**

TODO

Ask me to do a release if you wish to depend on something unreleased in `main` - paul@hammant.org

Tiny.java adds 2-3 seconds to your compile step depending on your CPU. I have a VERSION const in the Tiny source for you to check if you want.
