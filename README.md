# Tiny WebServer and WebSocketServer

A tiny web and socket server that depends only on JDK 21+ classes and are in a single source file: `Tiny.java`. 
Just a fun pair-programmed project, really. Imperfectly pair programmed with AIs. It is for use for to make small web/medium applications - perhaps not on the public web.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

The Tiny.java single source file provides a lightweight and flexible server implementation that supports both HTTP and 
WebSocket protocols. This single-source-file technology is designed to be easy to use and integrate into your projects.  
It uses a Java 8 lambda syntax (@FunctionalInterface) as many newer web frameworks do. It also uses the virtual thread
system in Java 21 and the JDK's built-in HTTP APIs rather than depending on Netty or Jetty.

## Web Server 

The `Tiny.WebServer` class allows you to create an HTTP server with minimal configuration. You can define filters and 
endpoints for different HTTP methods (GET, POST, PUT, DELETE and others), and process requests. 

The server supports:

- **Path-based Routing**: Define endpoints with path parameters and handle requests dynamically. Paths can be nested to create a hierarchical structure, allowing for organized and intuitive filter/endpoint management.
- **Static File Serving**: Serve static files from a specified directory with automatic content type detection.
- **Filters**: Apply filters to requests for pre-processing or access control.
- Fairly open

There are paths too, to which filters and endpoints can be attached, but don't themselves handle requests.

## Web Sockets Server

A coupled `Tiny.WebSocketServer` class provides WebSocket support, enabling communication back from the server to 
attached clients.  It can be used alone, but also integrated into the same path structure of the main server.
Admittedly that's a trick as the path and the length of the path are tge leftmost part of the message up
to the SocketServer. 

## Rationale

**I wanted to make something that**

1. A reliance on Java-8's lambdas - as close as regular Java can get to Groovy's builders for now
2. -- Related: Have a nested `path` construct for elegance and maintainability
3. Support a multi-module compositions. This for web-module separation to aid deployment and testing flexibility

**And in a second tier of must have features**

1. An attempt to coerce websockets into the same "nested path" composition
1. Follows Inversion of Control (IoC) idioms for dependency lookup
1. Aids testability wherever it can

**And a third tier of admittedly gratuitous or pet-peeve wishes**

1. No shared static state
1. Exist in a single source file, for no good reason.
1. A back-to-basics JDK & make build technology (Maven/Gradle not used for day to day bits)
1. Does not itself pollute stdout or force a logging framework on users.
4. Have no dependencies at all, outside the JDK

Single source file and no-maven used to be top level goals, but there's no strong rationale - they are firmly in 
the "let us see if that is possible" territory.  At one stage there was no build file at all, (just copy-pastable javac, 
java commands), then there was a shell script, then there was a makefile, which is where I should have started.

# Table of Contents

- [User Guide](#user-guide)
  - [Basic Use](#basic-use)
    - [End-points](#end-points)
    - [A Filter and an End-point](#a-filter-and-an-end-point)
    - [Two End-points within a Path](#two-end-points-within-a-path)
    - [A filter and an end-point within a path](#a-filter-and-an-end-point-within-a-path)
    - [A webSocket and endPoint within a path](#a-websocket-and-endpoint-within-a-path)
    - [Connecting to a WebSocket using Tiny.WebSocketClient](#connecting-to-a-websocket-using-tinywebsocketclient)
    - [Connecting to a WebSocket using JavaScript source file endpoint](#connecting-to-a-websocket-using-javascript-source-file-endpoint)
    - [Two WebSockets with Different Paths](#two-websockets-with-different-paths)
  - [Static File Serving](#static-file-serving)
  - [Composition](#composition)
  - [Testing your web app](#testing-your-web-app)
    - [Cuppa-Framework](#cuppa-framework)
    - [Mockito and similar](#mockito-and-similar)
  - [Error handling](#error-handling)
    - [In EndPoints Themselves](#in-endpoints-themselves)
    - [TinyWeb's Overridable Exception Methods](#tinywebs-overridable-exception-methods)
  - [Integrating other frameworks](#integrating-other-frameworks)
    - [Dependency Injection](#dependency-injection)
    - [TinyWeb usage statistics](#tinyweb-usage-statistics)
    - [Database/ ORM Technologies](#database-orm-technologies)
  - [Pitfalls](#pitfalls)
    - [Code in a 'path { }' block](#code-in-a-path--block)
    - [Application-scoped components](#application-scoped-components)
- [Secure Channels](#secure-channels)
  - [Securing HTTP Channels](#securing-http-channels)
  - [Securing WebSocket Channels](#securing-websocket-channels)
- [Build and Test of TinyWeb itself](#build-and-test-of-tinyweb-itself)
  - [Compiling TinyWeb](#compiling-tinyweb)
  - [Tests](#tests)
  - [Getting coverage reports for TinyWeb](#getting-coverage-reports-for-tinyweb)
  - [TinyWeb's own test results](#tinywebs-own-test-results)
- [Project & Source Repository](#project--source-repository)
- [Known Limitations](#known-limitations)
- [WIKI](#Wiki)
- [Contributions & Published versions](#contributions--published-versions)

# User guide

"Users" are developers, if that is not obvious. 

## Basic Use

### End-points

Here is a basic example of defining a GET endpoint using Tiny WebServer:

```java 
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
    endPoint(Tiny.HttpMethod.GET, "/hello", (req, res, context) -> {
        // req gives access to headers, etc
        res.write("Hello, World!");
    });
}}.start();
```

In this example, a GET endpoint is defined at the path `/hello`. When a request is made to http://localhost:8080/hello, the server responds with "Hello, World!"

### A Filter and an End-point

Here's an example of using a filter with an endpoint in Tiny Web:

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{

    // Apply a filter to check for a custom header
    filter(Tiny.Method.GET, "/secure", (req, res, context) -> {
        if (!req.getHeaders().containsKey("X-Auth-Token")) {
            res.write("Unauthorized", 401);
            return FilterResult.STOP; // Stop processing if unauthorized
        }
        return FilterResult.CONTINUE; // Continue processing
    });

    // Define a GET endpoint
    endPoint(Tiny.Method.GET, "/secure", (req, res, context) -> {
        res.write("Welcome to the secure endpoint!");
    });
        
}}.start();
```

In this example, a filter is applied to the `/secure` path to check for the presence of an "X-Auth-Token" header.
If the header is missing, the request is denied with a 401 status code. If the header is present, the request
proceeds to the endpoint, which responds with "Welcome to the secure endpoint!".

### Two End-points within a path

Here's an example of defining two endpoints within a single path using Tiny:

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
    path("/api", () -> {
        // Define the first GET endpoint
        endPoint(Tiny.Method.GET, "/hello", (req, res, context) -> {
            res.write("{ message:`Hello from the first endpoint!` }");
        });

        // Define the second GET endpoint
        endPoint(Tiny.HttpMethods.GET, "/goodbye", (req, res, context) -> {
          res.write("{ message:`Goodbye from the second endpoint!` }");
        });
    });
}}.start();
```

### A filter and an end-point within a path

Here's an example of using a filter to perform authentication and a logged-in user attribute to an endpoint within a path 
or not at all of there's no logged-in user.

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(-1)) {{
    path("/shopping", () -> {
        filter(Tiny.HttpMethods.GET, ".*", (req, res, context) -> {
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
        endPoint(Tiny.HttpMethods.GET, "/cart", (req, res, context) -> {
            Cart = carts.getCartFor(req.getAttribute("user"));
            // do something with cart
            // ignore the 'carts' class for the moment.
        });
    });
}}.start();
```

In this example, a filter is applied to all GET requests within the `/shopping` path to check for a 
"logged-in" cookie. The cookie is decrypted using a simple ROT47 algorithm to verify if the user 
is authenticated. If authenticated, the user's email is set as an attribute in the request, which 
is then accessed by the endpoint to respond with a message indicating the user is logged in.

In the **test suite**, there are two alternate test cases for authentication. One test case simulates a successful 
authentication by providing a valid "logged-in" cookie (7C65o6I2>A=6]4@>), which is decrypted to reveal a valid 
email address (fred@example.com). Recall, rot47 is only used for testing and you would never go live with that. 
The other test case simulates a failed authentication by providing an invalid cookie (aeiouaeiou), which does not decrypt 
to a valid email address. The difference in the cookie values determines whether the authentication passes or fails, 
demonstrating how the filter and endpoint interact to handle authenticated and unauthenticated requests - communicating
via an attribute if all is good.

### A webSocket and endPoint within a path

Here's an example of defining both a WebSocket and an HTTP endpoint within a single path using Tiny WebServer:

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
    path("/messenger", () -> {
        // Define a GET endpoint
        endPoint(Tiny.HttpMethods.GET, "/inboxStatus", (req, res, context) -> {
            res.write("API is running"); // not really what an api would do
        });

        // Define a WebSocket endpoint
        webSocket("/chatback", (message, sender, context) -> {
            String responseMessage = "Echo: " + new String(message, "UTF-8");
            sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
        });
    });
}}.start();
```

In this example, a GET endpoint is defined at `/messenger/inboxStatus` that responds with "API is running".
Additionally, a WebSocket endpoint is defined at `/messenger/chatback` that echoes back any message it
receives, prefixed with "Echo: ", which we admit isn't a real world example.

#### Connecting to a WebSocket using Tiny.WebSocketClient

Here's an example of how a client connects to a server WebSocket using `Tiny.WebSocketClient`:

```java
public class WebSocketClientExample {
    public static void main(String[] args) {
        try (Tiny.WebSocketClient client = new Tiny.WebSocketClient("ws://localhost:8081/messenger/chatback", "http://localhost:8080") {
            // Perform the WebSocket handshake
            client.performHandshake();

            // Send a message to the WebSocket server
            client.sendMessage("Hello WebSocket");

            // Receive a response from the WebSocket server
            client.receiveMessages("stop", response -> {
                System.out.println("Received: " + response);
                return true;
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

In this example, a `Tiny.WebSocketClient` is created to connect to a WebSocket server running on `localhost` at
port 8081. The client performs a WebSocket handshake, connects to the `/messenger/chatback` path, and prints the
response received from the server. 

The webSockets
appear within the same nested path structure of the composed webserver grammar. They are not really. They are not even the
same port on the server. At least, not in the current version of Tiny.

### WebSockets Performance

The `WebSocketBroadcastDemo` class demonstrates the capability of the Tiny WebSocketServer to handle a large number of concurrent WebSocket connections efficiently. This demo sets up a WebSocket server that can broadcast messages to multiple clients simultaneously.

#### Key Features:
- **Broadcasting**: The server can send messages to all connected clients, making it suitable for applications like chat rooms, live notifications, or real-time data feeds.
- **Scalability**: The demo has been tested with up to 20,000 concurrently connected clients, showcasing the server's ability to handle high loads. Different hardware might be able to do more. 
- **Virtual Threads**: Utilizes Java's virtual threads to manage connections, which helps in reducing the overhead associated with traditional thread management.
- **Connection Recovery**: The techniques shown `WebSocketBroadcastDemo` are designed to handle connection interruptions gracefully. If a client disconnects unexpectedly (including timeouts), the server attempts to manage the situation by allowing the client to reconnect and resume receiving broadcast messages. The demo showcases the server's ability to handle reconnections without significant performance degradation, ensuring a robust and resilient WebSocket communication environment.

This demo code highlights Tiny's potential for high-performance WebSocket applications, making it a viable choice for developers looking to implement real-time features in their projects.  Perhaps still, intranet solutions rather than web-scale. Note: An Erlang solution (client and server pieces in the same BEAM machine) could go much higher for unloaded server responses.  

## Static File Serving

Tiny.WebServer can serve static files from a specified directory. This is useful for serving assets like images, CSS, and JavaScript files directly from the server.

To serve static files, use the `serveStaticFilesAsync` method in your server configuration. This method takes two parameters: the base path for the static files and the directory from which to serve them.

Example:
```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
    serveStaticFilesAsync("/static", "/path/to/static/files");
}}.start();
```

In this example, any request to `/static` will be mapped to the files located in `/path/to/static/files`. For instance, a request to `/static/image.png` will serve the `image.png` file from the specified directory.

TODO: make an optional RAM cache.

### Best Practices

1. **Directory Structure**: Organize your static files in a clear directory structure. For example, separate images, stylesheets, and scripts into different folders.

2. **Caching**: Consider setting cache headers for static files to improve performance. This can be done by modifying the response headers in the `serveStaticFilesAsync` method.

3. **Security**: Ensure that your static file serving does not expose sensitive files. Use appropriate file permissions and validate file paths to prevent directory traversal attacks.

4. **Content Types**: Ensure that the correct content type is set for each file type. This can be handled automatically by the server, but it's good to verify.

5. **Performance**: For high-traffic applications, consider using a Content Delivery Network (CDN) to offload the serving of static files.

By following these guidelines, you can efficiently serve static files while maintaining security and performance.

### Still to do:  

1. A directory index capability, pretty or basic.
2. A way of overriding mime types per user-agent.

## Composition

We've covered paths, filters, endPoints, webSockets, and static file serving the low-level building blocks of Tiny applications.

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
    path("/advertisingCommissions", () -> {
      path("/selling", () -> {
          //TODO
      });
      path("/buying", () -> {
        //TODO
      });
    });
}}.start();
```


## Testing your web app

Testing is a critical part of developing reliable web applications. Tiny is just a library. You can write tests
using it in JUnit, TestNG, JBehave. You can use Mockito as you would do normally. They can't share a single port, but you could instantiate multiple WebServers on mny ports if you wanted to, as they don't share other than the JVM.

### Cuppa-Framework

The [Cuppa-Framework](https://github.com/cuppa-framework/cuppa) is what we are using for testing Tiny web applications due to its idiomatic style, which closely aligns  with Tiny's composition approach. It allows for expressive and readable test definitions. 

Example:

```java
import static org.forgerock.cuppa.Cuppa.*;

public class MyWebAppTest {
    {
        describe("GET /hello", () -> {
            before(() -> {
               // get logic here 
            });
            it("should return a greeting message", () -> {
                // Test of result of get here this probably uses a member var
            });
        });
    }
}
```

See the tests that use Cuppa linked to from the main [suite](tests/Suite.java).

There's no re

### Mockito and similar

Mockito is a powerful mocking framework that can be used to create mock objects for testing. It is particularly useful
for isolating components and testing interactions.

Consider the following code:

```java
public static class MyApp {

        public void foobar(Request req, Response res, Tiny.RequestContext ctx) {
            res.write(String.format("Hello, %s %s!", ctx.getParam("1"), ctx.getParam("2")));
        }

        public static Tiny.WebServer composeApplication(String[] args, MyApp app) {
            Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
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

In your tests, you could ignore the main() method, and call `composeApplication` with a mock `MyApp` instance that is an argument for that method. All your when(), verify() logic will work as you expect it to.

You could also override the `dep(..)` or `instantiateDep(..)` methods as a good place to hand mock collaborators in
to the "component under test." See later: TODO

## Error handling

### In EndPoints Themselves

When handling requests in Tiny, it's important to understand how to set HTTP response codes and customize
responses. HTTP response codes are crucial for indicating the result of a request to the client. Here's how you can
manage responses in Tiny:

#### Setting HTTP Response Codes

In Tiny, you can set the HTTP response code by using the `write` method of the `Tiny.Response` object. The `write`
method allows you to specify both the response content and the status code. Here's an example:

```java
endPoint(Tiny.HttpMethods.GET, "/example", (req, res, context) -> {
    // Set a 200 OK response
    res.write("Request was successful", 200);
});
```

In that example, the endpoint responds with a 200 OK status code, indicating that the request was successful.

TODO: example that sets a non-200 response code.

### Tiny's Overridable Exception Methods

In Tiny, exception handling is an important aspect of managing server and application errors. The framework
provides two overridable methods to handle exceptions: `serverException(ServerException e)` and `appHandlingException(Exception e)`. These methods allow you to customize how exceptions are logged or processed.

#### serverException(ServerException e)

The `serverException` method is called when a `ServerException` occurs. This typically involves issues related to
the server's **internal operations**, such as network errors or configuration problems. By default, this method shows the
exception message and stack trace to the standard error stream. You can override this method to implement custom
logging or error handling strategies.

Example:

```java
svr = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {
    {
      // paths, filters, endPoints setup as described before
    }
    @Override
    protected void serverException (ServerException e){
        // Custom logging logic
        System.err.println("Custom Server Exception: " + e.getMessage());
        e.printStackTrace(System.err);
        // or throw something
    }
};
```

#### appHandlingException(Exception e)

The `appHandlingException(Exception e)` method is invoked when an exception occurs within an endpoint or filter logic
This is useful for handling application-specific errors, such as invalid input or business logic failures. That would
nearly always be an endpoint of filter throwing `java.lang.RuntimeException` or `java.lang.Error`. They are not supposed
to, but they may do so, or a library they call does so. By default, this method logs the exception message and stack trace to the standard error stream. You can override it to provide custom error handling, such as sending alerts or writing to a log file.

Example:

```java
svr = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {
    {
        // paths, filters, endPoints setup as described before
    }

    @Override
    protected void appHandlingException (Exception e) {
        // Custom application error handling
        System.err.println("Custom Application Exception: " + e.getMessage());
        e.printStackTrace(System.err);
    }
};
```

By overriding these methods, you can tailor the exception handling behavior of your Tiny server to meet your
application's specific needs, ensuring that errors are managed effectively and transparently.

## Integrating other frameworks

### Dependency Injection

We can't integrate dependency injection with Tiny. The reason for that is handlers don't take strongly typed
dependencies in the `(req, resp, context)` functional interfaces, nor do those have constructors associated.

For it to be true Dependency Injection capable - for say injecting a `ShoppingCart` into a endpoint or filter - you would something like:

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
    endPoint(GET, "/users", /*ShoppingCart*/ cart, (req, res, context) -> {
        // do something with "cart" var
        res.write("some response");
    });
    // or...
    endPoint(GET, "/users", (req, res, context, /*ShoppingCart*/ cart) -> {
        // do something with "cart" var
        res.write("some response");
    });
}};
```

The problem is that method `endPoint(..)` has a fixed parameter list. It can't be extended to suit each specific use
of `endPoint` in an app that would list dependencies to be injected. Java itself does not allow this (yet). Instead, I have chosen to have a `getDep(..)` method on context `RequestContext` object. We acknowledge this is no longer Dependency
Injection. 

I think we are following the framing **Inversion of Control** (IoC) idioms with a lookup-style (interface
injection) way of getting dependencies into a endpoint (or filter). I contrasted the differences 21 years
ago - https://paulhammant.com/files/JDJ_2003_12_IoC_Rocks-final.pdf,
and I've built something rudimentary into Tiny that fits "interface injection" style.
The debate on Dependency Injection (DI) vs a possibly global-static Service Locator (that was popular before it) was put front and center by Martin Fowler
in https://www.martinfowler.com/articles/injection.html (I get a mention in the footnotes, but people occasionally
tell me to read it). "Interface Injection" is mentioned in that article, and predated the trend for D.I when Apache's defunct "Avalon Framework" promoted it.

Here's an example of our way. Again, this is not D.I., but is IoC from the pre-DI era.

```java
endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    ShoppingCart sc = ctx.dep(ShoppingCart.class);
    res.write("Cart item count: " + sc.cartCount());
});
```

This below is **also** not Dependency injection, nor is it IoC, but you could this way if really wanted to:

```java

// classic service locator style - should not do this, IMO

endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    // Eww - shared static state
    ShoppingCart sc = GlobalServiceLocator.getDepndency(ShoppingCart.class);
    res.write("Cart items count: " + sc.cartCount());
});
    
// classic "singleton" design pattern (not the Spring idiom) style - should not do this, IMO

endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    // Eww - shared static state
    ShoppingCart sc = ShoppingCart.getInstance();
    res.write("Cart items count: " + sc.cartCount());
});
```

This one again is more questionable - something registered at the same level as new `Tiny.WebServer() { .. };`

```java
endPoint(GET, "/howManyItemsInCart", (req, res, ctx) -> {
    
    ShoppingCart sc = getInstance(ShoppingCart.class); // from some scope outside than the endPoint() lambda
    res.write("Cart items count: " + sc.cartCount());
});
```

Say `ShoppingCart` depends on `ProductInventory`, but because you're following Inversion of Control you do not
want the `ProductInventory` instance directly used in any endPoint or filter. You would hide its instantiation in a scope of execution that would not be accessible to the endPoint() or filter() lambdas. Of course, if it is in the classpath, any code could do `new ProductInventory(..)` but we presume there are some secrets passed in through the constructor that ALSO are hidden from or filter lambdas making that pointless.

If you were using Spring Framework, you would have `ProductInventory` as `@Singleton` scope (a Spring idiom, not the Gang-of-Four design pattern). You would also have `ShoppingCart` as `@Scope("request")`

In the tests for Tiny, we have an example of use that features `endPoint(..)`, `ShoppingCart`
and `ProductInventory`

#### Actual Resolution

# Tiny usage statistics

Tiny provides a built-in statistics capability that allows you to monitor and analyze the performance of your app. This feature is useful for understanding the behavior of your application and identifying potential bottlenecks.

To access the collected statistics, you can override the `recordStatistics` method in your Tiny server implementation or instantiation. This method is called after each request is processed, and it receives a map of statistics that you can log, store, or analyze as needed.

Example:
```java
@Override
protected void recordStatistics(String path, Map<String, Object> stats) {
    // per request, after it is complete, a map of stats.
}
```
Stats, if to-String'd, look like:

```
{
  duration=2,
  path=/test/abc,
  endpoint=^/test/abc$,
  endpointDuration=50,
  filters=[
    FilterStat
    [
      path=^/test/.*c$,
      result=ok,
      duration=1
    ]
  ], 
  status=51
}
```

By overriding the `recordStatistics` method, you can do something toward:

- **Performance Monitoring**: Identify slow endpoints and optimize them for better performance.
- **Error Tracking**: Monitor the frequency and types of errors occurring in your application.
- **Filter Analysis**: Understand the impact of filters on request processing time and optimize them as needed.
- **Logging**


### Database/ ORM Technologies

Picking JDBI to do Object-Relational Mapping (ORM) for the sake of an example (GAV: org.jdbi:jdbi3-core:3.25.0):

```java
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;

public class MyDatabaseApp {
    private final Jdbi jdbi;
    private Tiny.WebServer server;
          
    public MyDatabaseApp() {
        // every endpoint/filter could directly do JDBC this way as ..
        final jdbi = Jdbi.create("jdbc:h2:mem:test");
        // .. `jdbi` would be in scope for all below

        server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
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

### Code in a 'path { }' block

When using Tiny, it's important to understand that any code placed outside of lambda blocks (such
as `path()`, `endPoint()`, or `filter()`) is executed only once during the server's instantiation. This
means that such code is not executed per request or per path hit, but rather when the server is being set up.

Here's an example of what not to do:

```java
Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
    path("/api", () -> {
        code().that().youThink("is per '/api/.*' invocation").but("it is not");
        // This code runs per request to /api
        endPoint(Tiny.HttpMethods.GET, "/hello", (req, res, context) -> {
            res.write("Code must be in lambda blocks");
        });
    });
}};
```

### Application-scoped components

We could have used the `ctx.getDep(..)` way of depending on JDBI, to aid mocking during test automation. But in the
above example, we just have an  instance `jdbi` that is visible to all the filters and endpoints duly composed.
It is up to you which way you develop with Tiny.

## Secure Channels

### Securing HTTP Channels

Currently, Tiny supports HTTP, which is suitable for development and testing environments. 
However, for production environments, it's crucial to secure HTTP channels using HTTPS. This can be achieved by 
fronting Tiny with a reverse proxy like Nginx or Apache, which can handle SSL/TLS termination.  There are also
reverse-reverse proxies (tunnels) that with code I have not done yet, could work.

### Securing WebSocket Channels

Same notes as above - the current implementation is `ws://` not `wss://` 

# Building and contributing to Tiny

see [BUILDING.md](BUILDING.md)