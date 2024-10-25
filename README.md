# TinyWeb.Server and TinyWeb.SocketServer

The `TinyWeb` class provides a lightweight and flexible server implementation that supports both HTTP and WebSocket protocols. This single-source-file technology is designed to be easy to use and integrate into your projects.

## Server

The `TinyWeb.Server` class allows you to create an HTTP server with minimal configuration. You can define routes for different HTTP methods (GET, POST, PUT, DELETE) and attach handlers to process requests. The server supports:

- **Path-based Routing**: Define endpoints with path parameters and handle requests dynamically.
- **Static File Serving**: Serve static files from a specified directory with automatic content type detection.
- **Filters**: Apply filters to requests for pre-processing or access control.

## WebSocket

The `TinyWeb.SocketServer` class provides WebSocket support, enabling real-time, bidirectional communication between the server and clients. Key features include:

- **Message Handling**: Register handlers for specific WebSocket paths to process incoming messages.
- **Secure Communication**: Supports WebSocket handshake and message framing for secure data exchange.
- **Integration with HTTP Server**: Seamlessly integrate WebSocket functionality with the HTTP server for a unified application architecture.

This technology is ideal for building lightweight web applications and services that require both HTTP and WebSocket capabilities in a single, easy-to-manage source file.

## Rationale

1. The primary rationale for developing this technology was to leverage nested `path()` functions to group endpoints together. This approach allows for a clean and intuitive way to define complex routing structures within the server. By nesting `path()` functions, developers can easily manage and organize routes, making the codebase more maintainable and scalable.
2. The secondary rationale was to get it all in one script and have not dependencies

# Build and Test

## Compiling TinyWeb

To compile `TinyWeb.java` into the `target/classes/` directory, use the following command:

```bash
mkdir -p target/classes
javac -d target/classes/ TinyWeb.java
```

That's it - no deps.

That makes:

``` 
ls target/classes/com/paulhammant/tinywebserver

TinyWeb$Context.class  TinyWeb$Filter.class  TinyWeb$PathContext.class  
TinyWeb$Server.class  TinyWeb$SocketServer$SocketMessageHandler.class 
TinyWeb$EndPoint.class  TinyWeb$FilterEntry.class  TinyWeb$Request.class  
TinyWeb$ServerException.class  TinyWeb$SocketServer.class  TinyWeb.class 
TinyWeb$MessageSender.class  TinyWeb$Response.class  TinyWeb$SocketClient.class      
TinyWeb$Method.class  TinyWeb$Server$1.class  TinyWeb$SocketClientJavascript.class 
TinyWeb$ExampleApp$1.class TinyWeb$ExampleApp.class
```

The last two are the built-in example app, and if we made a jar, we wouldn't bother to include those two.

```bash
find target/classes -name 'TinyWeb$ExampleApp*.class' -delete
jar cf TinyWeb.jar -C target/classes/ .
```

To compile `TinyWebTest.java` into the `target/test-classes/` directory you WILL need dependencies: (in `test_libs/`). 
Use the following to go get them:

```bash
mkdir -p test_libs
curl -L -o test_libs/annotations-13.0.jar https://repo1.maven.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.jar
curl -L -o test_libs/auto-service-annotations-1.1.1.jar https://repo1.maven.org/maven2/com/google/auto/service/auto-service-annotations/1.1.1/auto-service-annotations-1.1.1.jar
curl -L -o test_libs/byte-buddy-1.15.4.jar https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.15.4/byte-buddy-1.15.4.jar
curl -L -o test_libs/byte-buddy-agent-1.15.4.jar https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy-agent/1.15.4/byte-buddy-agent-1.15.4.jar
curl -L -o test_libs/checker-qual-3.43.0.jar https://repo1.maven.org/maven2/org/checkerframework/checker-qual/3.43.0/checker-qual-3.43.0.jar
curl -L -o test_libs/commons-exec-1.4.0.jar https://repo1.maven.org/maven2/org/apache/commons/commons-exec/1.4.0/commons-exec-1.4.0.jar
curl -L -o test_libs/cuppa-1.7.0.jar https://repo1.maven.org/maven2/org/forgerock/cuppa/cuppa/1.7.0/cuppa-1.7.0.jar
curl -L -o test_libs/error_prone_annotations-2.28.0.jar https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.28.0/error_prone_annotations-2.28.0.jar
curl -L -o test_libs/failureaccess-1.0.2.jar https://repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar
curl -L -o test_libs/guava-33.3.0-jre.jar https://repo1.maven.org/maven2/com/google/guava/guava/33.3.0-jre/guava-33.3.0-jre.jar
curl -L -o test_libs/hamcrest-3.0.jar https://repo1.maven.org/maven2/org/hamcrest/hamcrest/3.0/hamcrest-3.0.jar
curl -L -o test_libs/j2objc-annotations-3.0.0.jar https://repo1.maven.org/maven2/com/google/j2objc/j2objc-annotations/3.0.0/j2objc-annotations-3.0.0.jar
curl -L -o test_libs/jspecify-1.0.0.jar https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar
curl -L -o test_libs/jsr305-3.0.2.jar https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar
curl -L -o test_libs/kotlin-stdlib-1.9.23.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.23/kotlin-stdlib-1.9.23.jar
curl -L -o test_libs/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar https://repo1.maven.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
curl -L -o test_libs/mockito-core-5.14.2.jar https://repo1.maven.org/maven2/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar
curl -L -o test_libs/objenesis-3.3.jar https://repo1.maven.org/maven2/org/objenesis/objenesis/3.3/objenesis-3.3.jar
curl -L -o test_libs/okhttp-5.0.0-alpha.14.jar https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/5.0.0-alpha.14/okhttp-5.0.0-alpha.14.jar
curl -L -o test_libs/okio-jvm-3.9.0.jar https://repo1.maven.org/maven2/com/squareup/okio/okio-jvm/3.9.0/okio-jvm-3.9.0.jar
curl -L -o test_libs/opentelemetry-semconv-1.25.0-alpha.jar https://repo1.maven.org/maven2/io/opentelemetry/semconv/opentelemetry-semconv/1.25.0-alpha/opentelemetry-semconv-1.25.0-alpha.jar
curl -L -o test_libs/selenium-api-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-api/4.25.0/selenium-api-4.25.0.jar
curl -L -o test_libs/selenium-chrome-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-chrome-driver/4.25.0/selenium-chrome-driver-4.25.0.jar
curl -L -o test_libs/selenium-chromium-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-chromium-driver/4.25.0/selenium-chromium-driver-4.25.0.jar
curl -L -o test_libs/selenium-devtools-v129-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-devtools-v129/4.25.0/selenium-devtools-v129-4.25.0.jar
curl -L -o test_libs/selenium-devtools-v85-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-devtools-v85/4.25.0/selenium-devtools-v85-4.25.0.jar
curl -L -o test_libs/selenium-edge-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-edge-driver/4.25.0/selenium-edge-driver-4.25.0.jar
curl -L -o test_libs/selenium-firefox-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-firefox-driver/4.25.0/selenium-firefox-driver-4.25.0.jar
curl -L -o test_libs/selenium-http-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-http/4.25.0/selenium-http-4.25.0.jar
curl -L -o test_libs/selenium-ie-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-ie-driver/4.25.0/selenium-ie-driver-4.25.0.jar
curl -L -o test_libs/selenium-java-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-java/4.25.0/selenium-java-4.25.0.jar
curl -L -o test_libs/selenium-json-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-json/4.25.0/selenium-json-4.25.0.jar
curl -L -o test_libs/selenium-manager-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-manager/4.25.0/selenium-manager-4.25.0.jar
curl -L -o test_libs/selenium-os-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-os/4.25.0/selenium-os-4.25.0.jar
curl -L -o test_libs/selenium-remote-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-remote-driver/4.25.0/selenium-remote-driver-4.25.0.jar
curl -L -o test_libs/selenium-safari-driver-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-safari-driver/4.25.0/selenium-safari-driver-4.25.0.jar
curl -L -o test_libs/selenium-support-4.25.0.jar https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-support/4.25.0/selenium-support-4.25.0.jar
```

Then you can compile the tests class:

```bash
mkdir -p target/test-classes
javac -d target/test-classes -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/classes" TinyWebTest.java
```

To run the main method of `TinyWebTest.java`, which executes the tests using the Cuppa framework, use the following command:

```bash
java -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/test-classes:target/classes" com.paulhammant.tinywebserver.TinyWebTest
```

## TinyWeb.Server's Test Results

As mentioned, Cuppa-Framework is the tech used for testing and it spits out spec-style success/failure like so:

``` 
When using the ExampleApp server via sockets
    and accessing the Echoing GET endpoint
      ✓ returns the user profile for Jimmy
      ✓ returns the user profile for Thelma
    and accessing a nested path with parameters
      ✓ extracts parameters correctly from the nested path
      ✓ returns 404 for an incorrect nested path
    and applying filters
      ✓ allows access when the 'sucks' header is absent
      ✓ denies access when the 'sucks' header is present
    and serving static files
      ✓ returns 200 and serves a text file
      ✓ returns 404 for non-existent files
      ✓ returns 200 and serves a file from a subdirectory
      ✓ returns 200 and serves a non-text file
  When using the ExampleApp with Mockito
    and accessing the Greeting GET endpoint
      ✓ invokes the ExampleApp foobar method
  When testing the application inlined in Cuppa
    and the endpoint can extract parameters
      ✓ extracts parameters correctly from the path
      ✓ returns 404 when two parameters are provided for a one-parameter path
    and the endpoint can extract query parameters
      ✓ handles query parameters correctly
    and an application exception is thrown from an endpoint
      ✓ returns 500 and an error message for a runtime exception
    and the endpoint has query-string parameters
      ✓ parses query parameters correctly
    and response headers are sent to the client
      ✓ sets the custom header correctly
    and an exception is thrown from a filter
      ✓ returns 500 and an error message for a runtime exception in a filter
    and testing static file serving
      ✓ serves a static file correctly
      ✓ returns 404 for a non-existent static file
      ✓ prevents directory traversal attacks
    and using TinyWeb.SocketServer without TinyWeb.Server
      ✓ echoes three messages plus -1 -2 -3 back to the client
    and using TinyWeb.SocketServer with TinyWeb.Server
      ✓ echoes three messages plus -1 -2 -3 back to the client
    and using Selenium to subscribe in a browser
      ✓ echoes three messages plus -1 -2 -3 back to the client
```

ChatGPT estimates the path coverage for the TinyWeb class to be around 85-90%

I wish I could use Cuppa to generate example code in markdown, too.

