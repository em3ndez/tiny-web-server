# TinyWeb 

A tiny web and socket server that depends only on JDK 21+ classes and are in a single source file: `TinyWeb.java`

The TinyWeb single source file provides a lightweight and flexible server implementation that supports both HTTP and 
WebSocket protocols. This single-source-file technology is designed to be easy to use and integrate into your projects.  
It uses a Java 8 lambda syntax (@FunctionalInterface) as many newer web frameworks do. It also uses the virtual thread
system in Java 21 and the JDK's built-in HTTP APIs rather than depending on Netty or Jetty.

## The Web Server

The `TinyWeb.Server` class allows you to create an HTTP server with minimal configuration. You can define filters and 
endpoints for different HTTP methods (GET, POST, PUT, DELETE and others), and process requests. 

The server supports:

- **Path-based Routing**: Define endpoints with path parameters and handle requests dynamically. Paths can be nested to create a hierarchical structure, allowing for organized and intuitive filter/endpoint management.
- **Static File Serving**: Serve static files from a specified directory with automatic content type detection.
- **Filters**: Apply filters to requests for pre-processing or access control.
- Fairly open

There are paths too, to which filters and endpoints can be attached, but don't themselves handle requests.

## Its Web Sockets sibling

A coupled `TinyWeb.SocketServer` class provides WebSocket support, enabling communication back from the server to 
attached clients.  It can be used alone, but also integrated into the same path structure of the main server.
Admittedly that's a trick as the path and the length of the path are tge leftmost part of the message up
to the SocketServer.

## Rationale

I wanted to make something that:

1. A reliance on Java-8's lambdas - as close as regular Java can get to Groovy's builders
1. Have no dependencies at all, outside the JDK
2. Has a `path(..)` construct that groups other paths, endpoints and filters together. This approach allows for a clean and intuitive way to compose complex URL hierarchies within the server. More elegant and maintainable 
1. Using those, could take **a series of multiple such compositions** (nested paths/filter/endPoints). This for web-module separation to aid deployment and testing flexibility

And in a second tier:

1. No shared static state
1. Attempted to coerce websockets into the same nested path organization as is available for the web path composition
1. Exist in a single source file, for no good reason
1. Use JDK's own command for its build technology. Well, bash too.
1. Does not itself pollute stdout or force a logging framework on users.
1. Loosely follows Inversion of Control (IoC) idioms
1. Aids testability wherever it can

# Table of Contents

- [User Guide](#user-guide)
  - [Basic Use](#basic-use)
    - [EndPoints](#endpoints)
    - [A Filter and an EndPoint](#a-filter-and-an-endpoint)
    - [Two EndPoints within a Path](#two-endpoints-within-a-path)
    - [A Filter and an EndPoint within a Path](#a-filter-and-an-endpoint-within-a-path)
    - [A WebSocket and EndPoint within a Path](#a-websocket-and-endpoint-within-a-path)
    - [Connecting to a WebSocket using TinyWeb.SocketClient](#connecting-to-a-websocket-using-tinywebsocketclient)
    - [Connecting to a WebSocket using JavaScript Source File Endpoint](#connecting-to-a-websocket-using-javascript-source-file-endpoint)
    - [Two WebSockets with Different Paths](#two-websockets-with-different-paths)
  - [Testing Your Web App](#testing-your-web-app)
    - [Cuppa-Framework](#cuppa-framework)
    - [Mockito and similar](#mockito-and-similar)
  - [Error Handling](#error-handling)
    - [In EndPoints Themselves](#in-endpoints-themselves)
    - [TinyWeb's Overridable Exception Methods](#tinywebs-overridable-exception-methods)
  - [Integrating Other Frameworks](#integrating-other-frameworks)
    - [Dependency Injection](#dependency-injection)
    - [Database/ ORM Technologies](#database-orm-technologies)
  - [Pitfalls](#pitfalls)
- [Secure Channels](#secure-channels)
  - [Securing HTTP Channels](#securing-http-channels)
  - [Securing WebSocket Channels](#securing-websocket-channels)
- [TinyWeb Performance](#tinyweb-performance)
- [Build and Test of TinyWeb Itself](#build-and-test-of-tinyweb-itself)
  - [Compiling TinyWeb](#compiling-tinyweb)
  - [Tests](#tests)
  - [TinyWeb's Own Test Results](#tinywebs-own-test-results)
- [Project & Source Repository](#project--source-repository)

# User guide

"Users" are developers, if that's not obvious. 

## Basic Use

### EndPoints

Here's a basic example of defining a GET endpoint using TinyWeb:

```java 
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    endPoint(TinyWeb.Method.GET, "/hello", (req, res, context) -> {
        res.write("Hello, World!");
        // req gives access to headers, etc
    });
}}.start();
```

In this example, a GET endpoint is defined at the path `/hello`. When a request is made to this endpoint, the server 
responds with "Hello, World!". The server is set to listen on port 8080.

### A Filter and an EndPoint

Here's an example of using a filter with an endpoint in TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{

    // Apply a filter to check for a custom header
    filter(TinyWeb.Method.GET, "/secure", (req, res, context) -> {
        if (!req.getHeaders().containsKey("X-Auth-Token")) {
            res.write("Unauthorized", 401);
            return FilterResult.STOP; // Stop processing if unauthorized
        }
        return FilterResult.CONTINUE; // Continue processing
    });

    // Define a GET endpoint
    endPoint(TinyWeb.Method.GET, "/secure", (req, res, context) -> {
        res.write("Welcome to the secure endpoint!");
    });
        
}}.start();
```

In this example, a filter is applied to the `/secure` path to check for the presence of an "X-Auth-Token" header.
If the header is missing, the request is denied with a 401 status code. If the header is present, the request
proceeds to the endpoint, which responds with "Welcome to the secure endpoint!".

### Two EndPoints within a path

Here's an example of defining two endpoints within a single path using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    path("/api", () -> {
        // Define the first GET endpoint
        endPoint(TinyWeb.Method.GET, "/hello", (req, res, context) -> {
            res.write("Hello from the first endpoint!");
        });

        // Define the second GET endpoint
        endPoint(TinyWeb.Method.GET, "/goodbye", (req, res, context) -> {
            res.write("Goodbye from the second endpoint!");
        });
    });
}}.start();
```

In this example, two GET endpoints are defined within the `/api` path. The first endpoint responds with "Hello
from the first endpoint!" when a request is made to `/api/hello`, and the second endpoint responds with
"Goodbye from the second endpoint!" when a request is made to `/api/goodbye`.

You could place your Unauthorized/401 security check inside "/api" path and have it apply to both endPoints

### A filter and an EndPoint within a path

Here's an example of using a filter to perform authentication and passing attributes to an endpoint within a path 
using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    path("/api", () -> {
        filter(TinyWeb.Method.GET, ".*", (req, res, context) -> {
            String allegedlyLoggedInCookie = req.getCookie("logged-in");
            // This test class only performs rot47 on the cookie passed in.
            // That's not secure in the slightest. See https://rot47.net/
            Authentication auth = IsEncryptedByUs.decrypt(allegedlyLoggedInCookie);
            if (!auth.authentic) {
                res.write("Try logging in again", 403);
                return FilterResult.STOP;
            } else {
                req.setAttribute("user", auth.user());
            }
            return FilterResult.CONTINUE; // Continue processing
        });
        endPoint(TinyWeb.Method.GET, "/attribute-test", (req, res, context) -> {
            res.write("User Is logged in: " + req.getAttribute("user"));
        });
    });
}}.start();

public static class IsEncryptedByUs {
    public static Authentication decrypt(String allegedlyLoggedInCookie) {
        String rot47ed = rot47(allegedlyLoggedInCookie);
        // Check if it's an email address
        if (rot47ed.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$")) {
            return new Authentication(true, rot47ed);
        } else {
            return new Authentication(false, null);
        }
    }
}

public record Authentication(boolean authentic, String user) {}

private static String rot47(String input) {
    StringBuilder result = new StringBuilder();
    for (char c : input.toCharArray()) {
        if (c >= '!' && c <= 'O') {
            result.append((char) (c + 47));
        } else if (c >= 'P' && c <= '~') {
            result.append((char) (c - 47));
        } else {
            result.append(c);
        }
    }
    return result.toString();
}
```

In this example, a filter is applied to all GET requests within the `/api` path to check for a 
"logged-in" cookie. The cookie is decrypted using a simple ROT47 algorithm to verify if the user 
is authenticated. If authenticated, the user's email is set as an attribute in the request, which 
is then accessed by the endpoint to respond with a message indicating the user is logged in.

In the test suite, there are two alternate test cases for authentication. One test case simulates a successful 
authentication by providing a valid "logged-in" cookie (7C65o6I2>A=6]4@>), which is decrypted to reveal a valid 
email address (fred@example.com). Recall, rot47 is only used for testing and you would never go live with that. 
The other test case simulates a failed authentication by providing an invalid cookie (aeiouaeiou), which does not decrypt 
to a valid email address. The difference in the cookie values determines whether the authentication passes or fails, 
demonstrating how the filter and endpoint interact to handle authenticated and unauthenticated requests - communicating
via an attribute if all is good.

### A webSocket and endPoint within a path

Here's an example of defining both a WebSocket and an HTTP endpoint within a single path using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
    path("/api", () -> {
        // Define a GET endpoint
        endPoint(TinyWeb.Method.GET, "/status", (req, res, context) -> {
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

#### Connecting to a WebSocket using TinyWeb.SocketClient

Here's an example of how a client connects to a server WebSocket using `TinyWeb.SocketClient`:

```java
public class WebSocketClientExample {
    public static void main(String[] args) {
        try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
            // Perform the WebSocket handshake
            client.performHandshake();

            // Send a message to the WebSocket server
            client.sendMessage("/chat", "Hello WebSocket");

            // Receive a response from the WebSocket server
            String response = client.receiveMessage();
            System.out.println("Received: " + response);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

In this example, a `TinyWeb.SocketClient` is created to connect to a WebSocket server running on `localhost` at
port 8081. The client performs a WebSocket handshake, sends a message to the `/chat` path, and prints the
response received from the server. On the wire, the path and message are put in a specific structure for sending to
the server. That's opinionated, whereas the regular HTTP side of TinyWeb is not. This is to make the webSockets
appear within the same nested path structure of the composed server grammar. They're not really - not even the
same port to the server.

#### Connecting to a WebSocket using JavaScript source file endpoint

Here's an example of how to connect to a WebSocket using the JavaScript version of `TinyWeb.SocketClient`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>WebSocket Test</title>
    <script src="/javascriptWebSocketClient.js"></script>
</head>
<body>
    <h1>WebSocket Message Display</h1>
    <pre id="messageDisplay"></pre>
    <script>
        const tinyWebSocketClient = new TinyWeb.SocketClient('localhost', 8081);

        async function example() {
            try {
                await tinyWebSocketClient.waitForOpen();
                await tinyWebSocketClient.sendMessage('/chat', 'Hello WebSocket');

                const response = await tinyWebSocketClient.receiveMessage();
                document.getElementById('messageDisplay').textContent = 'Received: ' + response;

                await tinyWebSocketClient.close();
            } catch (error) {
                console.error('WebSocket error:', error);
            }
        }

        example();
    </script>
</body>
</html>
```

In this example, a JavaScript version of `TinyWeb.SocketClient` (`TinyWeb.JavaScriptSocketClient`) is created in 
JavaScript to connect to a WebSocket server running on `localhost` at port 8081. The client waits for the 
connection to open, sends a message to the `/chat` path, and displays the response received from the server in 
the browser.

**Making the JavaScript WebSocket Client available to webapps**

In the example where we connect to a WebSocket using the JavaScript `TinyWeb.SocketClient`,
the server needs to serve the JavaScript client code to the browser. This is done by defining one more endpoint
that responds with the JavaScript code when requested:

```java
endPoint(TinyWeb.Method.GET, "/javascriptWebSocketClient.js",new TinyWeb.JavascriptSocketClient());
```
This is to honor the server-side need for path & message to be in a specific opinionated structure.

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

## Testing Your Web App

Testing is a critical part of developing reliable web applications. TinyWeb is just a library. You can write tests
using it in JUnit, TestNG, JBehave.

### Cuppa-Framework

The Cuppa-Framework is what we are using for testing TinyWeb applications due to its idiomatic style, which closely aligns
with TinyWeb's composition approach. It allows for expressive and readable test definitions.

Example:

```java
import static org.forgerock.cuppa.Cuppa.*;

public class MyWebAppTest {
    {
        describe("GET /hello", () -> {
            it("should return a greeting message", () -> {
                // Test logic here
            });
        });
    }
}
```

[See the tests that use Cuppa](TinyWebTests.java)

### Mockito and similar

Mockito is a powerful mocking framework that can be used to create mock objects for testing. It is particularly useful
for isolating components and testing interactions.

Consider the following code:

```java
public static class MyApp {

        public void foobar(Request req, Response res, TinyWeb.RequestContext ctx) {
            res.write(String.format("Hello, %s %s!", ctx.getParam("1"), ctx.getParam("2")));
        }

        public static TinyWeb.Server composeApplication(String[] args, MyApp app) {
            TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
                endPoint(GET, "/greeting/(\\w+)/(\\w+)", app::foobar);
            }};
            return server;
        }
    }

    public static void main(String[] args) {
        MyApp.composeApplication(args, new MyApp()).start();
    }
}
```

In your tests, you could ignore the main() method, and call `composeApplication` with a mock `MyApp` instance. All
your when(), verify() logic will work as you expect it to.

You could also override the `dep(..)` or `instantiateDep(..)` methods as a good place to hand mock collaborators in
to the "component under test."

## Error handling

### In EndPoints Themselves

When handling requests in TinyWeb, it's important to understand how to set HTTP response codes and customize
responses. HTTP response codes are crucial for indicating the result of a request to the client. Here's how you can
manage responses in TinyWeb:

#### Setting HTTP Response Codes

In TinyWeb, you can set the HTTP response code by using the `write` method of the `TinyWeb.Response` object. The `write`
method allows you to specify both the response content and the status code. Here's an example:

```java
endPoint(TinyWeb.Method.GET, "/example", (req, res, context) -> {
    // Set a 200 OK response
    res.write("Request was successful", 200);
});
```

In that example, the endpoint responds with a 200 OK status code, indicating that the request was successful.

### TinyWeb's Overridable Exception Methods

In TinyWeb, exception handling is an important aspect of managing server and application errors. The framework
provides two overridable methods to handle exceptions: `serverException(e)` and `appHandlingException(Exception e)`.
These methods allow you to customize how exceptions are logged or processed.

#### serverException(e)

The `serverException` method is called when a `ServerException` occurs. This typically involves issues related to
the server's internal operations, such as network errors or configuration problems. By default, this method logs the
exception message and stack trace to the standard error stream. You can override this method to implement custom
logging or error handling strategies.

Example:

```java
svr = new TinyWeb.Server(8080, -1) {
    {
      // paths, filters, endPoints
    }
    @Override
    protected void serverException (ServerException e){
        // Custom logging logic
        System.err.println("Custom Server Exception: " + e.getMessage());
        e.printStackTrace(System.err);
    }
};
```

#### appHandlingException(Exception e)

The `appHandlingException(Exception e)` method is invoked when an exception occurs within an endpoint or filter.
This is useful for handling application-specific errors, such as invalid input or business logic failures. That would
nearly always be an endpoint of filter throwing `java.lang.RuntimeException` or `java.lang.Error`. They are not supposed
to, but they may do so. By default, this method logs the exception message and stack trace to the standard error
stream. You can override it to provide custom error handling, such as sending alerts or writing to a log file.

Example:

```java
svr = new TinyWeb.Server(8080, -1) {
    {
        // paths, filters, endPoints
    }

    @Override
    protected void appHandlingException (Exception e) {
        // Custom application error handling
        System.err.println("Custom Application Exception: " + e.getMessage());
        e.printStackTrace(System.err);
    }
};
```

By overriding these methods, you can tailor the exception handling behavior of your TinyWeb server to meet your
application's specific needs, ensuring that errors are managed effectively and transparently.

## Integrating other frameworks

### Dependency Injection

We can't integrate dependency injection with TinyWeb. The reason for that is handlers don't take strongly typed
dependencies in the `(req, resp, context)` functional interfaces, nor do those have constructors associated.

For it to be true Dependency Injection capable - for say injecting a `ShoppingCart` into a endpoint or filter - you would something like:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    endPoint(GET, "/users", /*ShoppingCart*/ cart, (req, res, context) -> {
        // do something with "cart" var
        res.write("some response");
    });
}};
```

The problem is that method `endPoint(..)` has a fixed parameter list. It can't be extended to suit each specific use
of `endPoint` in an app that would list dependencies to be injected. Instead, we have chosen
to have a `getDep(..)` method on context `RequestContext` object. We acknowledge this is no longer Dependency
Injection.

We think we are instead following the framing **Inversion of Control** (IoC) idiom with a lookup-style (interface
injection) way of getting dependencies into a endpoint (or filter). I contrast the differences 21 years
ago - https://paulhammant.com/files/JDJ_2003_12_IoC_Rocks-final.pdf,
and I've built something rudimentary into TinyWeb that fits interface injection style.
The debate on Dependency Injection
(DI) vs a possibly global-static Service Locator (that was popular before it) was put front and center by Martin Fowler
in https://www.martinfowler.com/articles/injection.html (I get a mention in the footnotes, but people occasionally
tell me to read it).

Here's an example of our way. Again, this is not D.I., but is IoC from the pre-DI era.

```java
endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    ShoppingCart sc = ctx.dep(ShoppingCart.class);
    res.write("Cart item count: " + sc.cartCount());
});
```

This below is also not Dependency injection, nor is it IoC, but you could this way if really wanted to:

```java

// classic service locator style - should not do this, IMO

endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    ShoppingCart sc = GlobalServiceLocator.getDepndency(ShoppingCart.class);
    res.write("Cart items count: " + sc.cartCount());
});
    
// classic singleton design-pattern style - should not do this, IMO

endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    ShoppingCart sc = ShoppingCart.getInstance();
    res.write("Cart items count: " + sc.cartCount());
});
```

This one again is not Dependency injection, but could be IoC depending on implementation:

```java
endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    ShoppingCart sc = getInstance(ShoppingCart.class); // from some scope outside than the endPoint() lambda
    res.write("Cart items count: " + sc.cartCount());
});
```

Say `ShoppingCart` depends on `ProductInventory`, but because you're following Inversion of Control you do not
want the `ProductInventory` instance directly used in any endPoint or filter. You would hide its instantiation in a scope of execution
that would not be accessible to the endPoint or filter lambdas. Of course, if it is in the classpath, any code could do
`new ProductInventory(..)` but we presume there are some secrets passed in through the constructor that ALSO are
hidden from or filter lambdas making that pointless.

If you were using Spring Framework, you would have `ProductInventory` as `@Singleton` scope (an idiom, not the Gang-of-Four
design pattern). You would also have `ShoppingCart` as `@Scope("request")`

In [TinyWebTests](TinyWebTests.java), we have an example of use that features `endPoint(..)`, `ShoppingCart`
and `ProductInventory`

### Database/ ORM Technologies

Picking JDBI to do Object-Relational Mapping (ORM) for the sake of an example (GAV: org.jdbi:jdbi3-core:3.25.0):

```java
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;

public class MyDatabaseApp {
    private final Jdbi jdbi;
    private TinyWeb.Server server;
          
    public MyDatabaseApp() {
        jdbi = Jdbi.create("jdbc:h2:mem:test");

        server = new TinyWeb.Server(8080, -1) {{
            endPoint(GET, "/users", (req, res, context) -> {
                List<String> users = jdbi.inTransaction(handle ->
                    handle.createQuery("SELECT name FROM users")
                            .mapTo(String.class)
                            .list());
                res.write(String.join(", ", users));
            });
  
            endPoint(POST, "/addUser", (req, res, context) -> {
                jdbi.useTransaction(TransactionIsolationLevel.SERIALIZABLE, handle -> {
                    handle.execute("INSERT INTO users (name) VALUES (?)", req.getBody());
                });
                res.write("User added successfully", 201);
            });
        }}.start();
    }

    public static void main(String[] args) {
        new MyDatabaseApp();
    }
}
```

## Pitfalls

When using TinyWeb, it's important to understand that any code placed outside of lambda blocks (such
as `path()`, `endPoint()`, or `filter()`) is executed only once during the server's instantiation. This
means that such code is not executed per request or per path hit, but rather when the server is being set up.

Here's an example of what not to do:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    path("/api", () -> {
        code().thatYouThink("is per to /api invocation, but it is not");
        // This code runs per request to /api
        endPoint(TinyWeb.Method.GET, "/hello", (req, res, context) -> {
            res.write("Code must be in lambda blocks");
        });
    });
}};
```


We could have used the `ctx.getDep(..)` way of depending on JDBI, to aid mocking during test automation. But in the
above example, we just have an  instance `jdbi` that is visible to all the filters and endpoints duly composed.
It is up to you which way you develop with TinyWeb

## Secure Channels

### Securing HTTP Channels

Currently, TinyWeb supports HTTP, which is suitable for development and testing environments. 
However, for production environments, it's crucial to secure HTTP channels using HTTPS. This can be achieved by 
fronting TinyWeb with a reverse proxy like Nginx or Apache, which can handle SSL/TLS termination. 

### Securing WebSocket Channels

TinyWeb currently supports WebSocket (WS) connections, which are not encrypted. For secure communication, it's 
important to use Secure WebSocket (WSS) connections. Similar to HTTP, you can achieve this by using a reverse proxy 
that supports SSL/TLS termination for WebSockets. 

## TinyWeb Performance

Performance testing for TinyWeb has not been extensively conducted. However, due to its lightweight nature and minimal 
dependencies, TinyWeb is expected to perform efficiently for small to medium-sized applications.

# Build and Test of TinyWeb itself

## Compiling TinyWeb

To compile `TinyWeb.java` into the `target/classes/` directory, use the following command:

```bash
mkdir -p target/classes
javac -d target/classes/ TinyWeb.java
```

That's it - no deps, and 1.5 seconds of compilation time on my mid-range Chromebook. That makes 31 `.class` files

```bash
jar cf TinyWeb-$(cat TinyWeb.java | grep VERSION | cut -d '"' -f 2).jar -C target/classes/ .
```

that is about 37K in size.

## Tests

To compile TinyWeb's tests into the `target/test-classes/` directory you WILL need dependencies: (in `test_libs/`). 
Use the following to go get them:

```bash
curl -s https://raw.githubusercontent.com/paul-hammant/mvn-dep-getter/refs/heads/main/mvn-dep-getter.py | python3 - org.forgerock.cuppa:cuppa:1.7.0,org.hamcrest:hamcrest:3.0,com.squareup.okhttp3:okhttp:5.0.0-alpha.14,org.mockito:mockito-core:5.14.2,org.seleniumhq.selenium:selenium-java:4.26.0 test_libs
```

That is a Python script, so you'll need Python installed. Also Maven, but THIS project does not use Maven in any other way.

Then you can compile the tests class:

```bash
mkdir -p target/test-classes
find tests -name "*.java" > tests/sources.txt
javac -d target/test-classes -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/classes" @tests/sources.txt
```

To run the main method of `Suite.java`, which executes the test suite using the Cuppa framework, use the following command:

```bash
java -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/test-classes:target/classes" tests.Suite
```

### Getting coverage reports for TinyWeb

Get JaCoCo

``` 
curl -L -o jacocoagent.jar https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar
curl -L -o jacococli.jar https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar       
```

Instrument 

``` 
java -javaagent:jacocoagent.jar=destfile=jacoco.exec -cp "$(find test_libs -name '*.jar' | tr '\n' ':')target/test-classes:target/classes" tests.Suite
```

Print report

``` 
mkdir -p target/srcForJaCoCo/com/paulhammant/tnywb/
cp TinyWeb.java target/srcForJaCoCo/com/paulhammant/tnywb/
java -jar jacococli.jar report jacoco.exec --classfiles target/classes --sourcefiles target/srcForJaCoCo --html jacoco-report
```

## TinyWeb's own test results

As mentioned, Cuppa-Framework is the tech used for testing, and it outputs spec-style success/failure like so, 
from `TinyWebTests.java`, and part of the tested code is from `TinyWeb.ExampleApp` including the 
`TinyWeb.ExampleApp.exampleComposition(..)` launch of a whole app to test.

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

ChatGPT estimates the path coverage for the TinyWeb class to be around 90-95%

I wish I could use Cuppa to generate example code in markdown, too. Maybe I'll raise that feature request.

## Project & Source Repository

The project is organized as follows:

- **`TinyWeb.java`**: The main source file containing the implementation of the TinyWeb server and related classes. No deps outside the JDK.
- **`TinyWebTests.java`**: Contains tests for the TinyWeb server using the Cuppa framework. Package is different to the TinyWeb class in order to not accidentally take advantage of public/package/private visibility mistakes which can't neatly be tested for otherwise.
- **`README.md`**: This file, providing an overview and documentation of the project.
- **`test_libs/`**: Directory containing dependencies required for running tests - built by curl scripts in this README
- **`target/classes/`**: Directory where compiled classes are stored. 
- **`target/test-classes/`**: Directory where compiled test classes are stored.

Notes:

1. `target` is what Maven would use, but we're not using Maven for this repo (we did to discover the dependency tree)
2. Both Java sources have packages. While it is conventional to have sources in a dir tree that represents the package, you don't have to

Stats:

Source file `TinyWeb.java` has Approximately 666 lines of consequential code. 

``` 
# `cloc` counts lines of code
# don't count } on their own on a line, or }); or }};
cat TinyWeb.java | sed '/\w*}\w*/d' | sed '/\w*}];\w*/d' | sed '/\w*});\w*/d' > tmpfile.java
cloc tmpfile.java
rm tempfile.java
```

The README and tests are heading toward that size but are still under.
