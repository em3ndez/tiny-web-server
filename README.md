# TinyWeb.Server and TinyWeb.SocketServer

The `TinyWeb` class provides a lightweight and flexible server implementation that supports both HTTP and WebSocket 
protocols. This single-source-file technology is designed to be easy to use and integrate into your projects.  It uses
a key Java 8 syntax (Functional-Interface and lambdas) as many newer web frameworks do. It also uses the virtual thread
system in Java 21 and the JDK's built-in HTTP APIs rather than Netty or Jetty.

## Server

The `TinyWeb.Server` class allows you to create an HTTP server with minimal configuration. You can define routes for different HTTP methods (GET, POST, PUT, DELETE) and attach handlers to process requests. The server supports:

- **Path-based Routing**: Define endpoints with path parameters and handle requests dynamically.
- **Static File Serving**: Serve static files from a specified directory with automatic content type detection.
- **Filters**: Apply filters to requests for pre-processing or access control.
- Fairly open

## WebSocket

The `TinyWeb.SocketServer` class provides WebSocket support, enabling real-time, bidirectional communication between the server and clients. Key features include:

- **Message Handling**: Register handlers for specific WebSocket paths to process incoming messages.
- **Secure Communication**: Supports WebSocket handshake and message framing for secure data exchange.
- **Integration with HTTP Server**: Seamlessly integrate WebSocket functionality with the HTTP server for a unified application architecture.

These two together are ideal for building lightweight web applications and services that require both HTTP and WebSocket 
capabilities. TinyWeb.SocketServer can be run separately.

## Rationale

I wanted to make something that:

1. Had nested `path( .. )` lambda functions to group endpoints together. This approach allows for a clean and intuitive way to define complex routing structures within the server. By nesting `path()` functions, developers can easily manage and organize routes, making the codebase more maintainable and scalable.
2. Could maybe be in one source file and have no dependencies at all
3. Does not log, and has not picked a logging framework, but laves that open as an implementation detail. I wrote much of https://cwiki.apache.org/confluence/display/avalon/AvalonNoLogging back in 2003 or so.

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

The last two are the built-in example app, and if we made a jar, we wouldn't bother to include those include them.

```bash
find target/classes -name 'TinyWeb$ExampleApp*.class' -delete
jar cf TinyWeb.jar -C target/classes/ .
```

## Tests

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

As mentioned, Cuppa-Framework is the tech used for testing, and it outputs spec-style success/failure like so:

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

I wish I could use Cuppa to generate example code in markdown, too. Maybe I'll raise that feature request.

# Examples

## Basic Use

### endPoint

Here's a basic example of defining a GET endpoint using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    endPoint(TinyWeb.Method.GET, "/hello", (req, res, params) -> {
        res.write("Hello, World!");
        // req gives access to headers, etc
    });
}}.start();
```

In this example, a GET endpoint is defined at the path `/hello`. When a request is made to this endpoint, the server responds with "Hello, World!". The server is set to listen on port 8080.

### path and filter (and endPoints)

Here's an example of using a filter with an endpoint in TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{

    // Apply a filter to check for a custom header
    filter(TinyWeb.Method.GET, "/secure", (req, res, params) -> {
        if (!req.getHeaders().containsKey("X-Auth-Token")) {
            res.write("Unauthorized", 401);
            return false; // Stop processing if unauthorized
        }
        return true; // Continue processing
    });

    // Define a GET endpoint
    endPoint(TinyWeb.Method.GET, "/secure", (req, res, params) -> {
        res.write("Welcome to the secure endpoint!");
    });
        
}}.start();
```

In this example, a filter is applied to the `/secure` path to check for the presence of an "X-Auth-Token" header. 
If the header is missing, the request is denied with a 401 status code. If the header is present, the request 
proceeds to the endpoint, which responds with "Welcome to the secure endpoint!".

### two endPoints within a path

Here's an example of defining two endpoints within a single path using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    path("/api", () -> {
        // Define the first GET endpoint
        endPoint(TinyWeb.Method.GET, "/hello", (req, res, params) -> {
            res.write("Hello from the first endpoint!");
        });

        // Define the second GET endpoint
        endPoint(TinyWeb.Method.GET, "/goodbye", (req, res, params) -> {
            res.write("Goodbye from the second endpoint!");
        });
    });
}}.start();
```

In this example, two GET endpoints are defined within the `/api` path. The first endpoint responds with "Hello 
from the first endpoint!" when a request is made to `/api/hello`, and the second endpoint responds with 
"Goodbye from the second endpoint!" when a request is made to `/api/goodbye`.

You could place your Unauthorized/401 security check inside "/api" path and have it apply to both endPoints

### webSocket and endPoint within a path

Here's an example of defining both a WebSocket and an HTTP endpoint within a single path using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
    path("/api", () -> {
        // Define a GET endpoint
        endPoint(TinyWeb.Method.GET, "/status", (req, res, params) -> {
            res.write("API is running");
        });

        // Define a WebSocket endpoint
        webSocket("/chat", (message, sender) -> {
            String responseMessage = "Echo: " + new String(message, "UTF-8");
            sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
        });
    });
}}.start();
```

In this example, a GET endpoint is defined at `/api/status` that responds with "API is running". 
Additionally, a WebSocket endpoint is defined at `/api/chat` that echoes back any message it 
receives, prefixed with "Echo: ", which we admit isn't a real world example.

TODO: show what Java code would look like to conntect to the webSocket via TinyWeb.SocketClient

### Two WebSockets with Different Paths

Here's an example of defining two WebSocket endpoints with different paths using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
    path("/api", () -> {
        // Define the first WebSocket endpoint
        webSocket("/chat", (message, sender) -> {
            String responseMessage = "Chat Echo: " + new String(message, "UTF-8");
            sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
        });

        // Define the second WebSocket endpoint
        webSocket("/notifications", (message, sender) -> {
            String responseMessage = "Notification: " + new String(message, "UTF-8");
            sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
        });
    });
}}.start();
```

In this example, two WebSocket endpoints are defined within the `/api` path. The first WebSocket 
endpoint at `/api/chat` echoes back any message it receives, prefixed with "Chat Echo: ". The second 
WebSocket endpoint at `/api/notifications` echoes back messages prefixed with "Notification: ". The server keeps a 
big map of paths and websockets open to clients, and if this were a single web-app for one person, it'd be two
websocket channels back to the same server. Two concurrently connected people in the same webapp would be mean 
four concurrently connected channels.

## Don't do this

When using TinyWeb, it's important to understand that any code placed outside of lambda blocks (such 
as `path()`, `endPoint()`, or `filter()`) is executed only once during the server's instantiation. This 
means that such code is not executed per request or per path hit, but rather when the server is being set up.

Here's an example of what not to do:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    path("/api", () -> {
        code().thatYouThink("is per to /api invocation, but it is not");
        // This code runs per request to /api
        endPoint(TinyWeb.Method.GET, "/hello", (req, res, params) -> {
            res.write("Code must be in lambda blocks");
        });
    });
}};
```
