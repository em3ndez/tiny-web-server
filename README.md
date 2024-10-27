# TinyWeb 

Server and SocketServer depending only on the JDK and in a single source file.

## Table of Contents
- [Web Server](#web-server)
- [Web Sockets](#web-sockets)
- [Rationale](#rationale)
- [Quick user guide](#quick-user-guide)
  - [Basic Use](#basic-use)
  - [EndPoints](#endpoints)
  - [A Filter and an EndPoints](#a-filter-and-an-endpoints)
  - [Two EndPoints within a path](#two-endpoints-within-a-path)
  - [A filter and an EndPoint within a path](#a-filter-and-an-endpoint-within-a-path)
  - [A webSocket and endPoint within a path](#a-websocket-and-endpoint-within-a-path)
  - [Connecting to a WebSocket using TinyWeb.SocketClient](#connecting-to-a-websocket-using-tinywebsocketclient)
  - [Connecting to a WebSocket using JavaScript source file endpoint](#connecting-to-a-websocket-using-javascript-source-file-endpoint)
  - [Two WebSockets with Different Paths](#two-websockets-with-different-paths)
- [Thoughts on WebSockets](#thoughts-on-websockets)
  - [Short Messages with Follow-up GET Requests](#short-messages-with-follow-up-get-requests)
- [Secure Channels](#secure-channels)
  - [Securing HTTP Channels](#securing-http-channels)
  - [Securing WebSocket Channels](#securing-websocket-channels)
- [Performance](#performance)
- [Error handling](#error-handling)
  - [In EndPoints Themselves](#in-endpoints-themselves)
  - [TinyWeb's Overridable Exception Methods](#tinywebs-overridable-exception-methods)
  - [Input Validation](#input-validation)
- [Integrating other frameworks](#integrating-other-frameworks)
  - [Dependency Injection](#dependency-injection)
  - [ORM Technologies](#orm-technologies)
- [Don't do this](#dont-do-this)
- [Testing Your Web App](#testing-your-web-app)
  - [Cuppa-Framework](#cuppa-framework)
  - [JUnit and TestNG](#junit-and-testng)
  - [Mockito](#mockito)
- [Build and Test of TinyWeb itself](#build-and-test-of-tinyweb-itself)
  - [Compiling TinyWeb](#compiling-tinyweb)
  - [Tests](#tests)
  - [TinyWeb's own test results](#tinywebs-own-test-results)

The `TinyWeb` single source file provides a lightweight and flexible server implementation that supports both HTTP and 
WebSocket protocols. This single-source-file technology is designed to be easy to use and integrate into your projects.  
It uses a Java 8 lambda syntax (@FunctionalInterface) as many newer web frameworks do. It also uses the virtual thread
system in Java 21 and the JDK's built-in HTTP APIs rather than depending on Netty or Jetty.

## Web Server

The `TinyWeb.Server` class allows you to create an HTTP server with minimal configuration. You can define routes for 
different HTTP methods (GET, POST, PUT, DELETE) and attach handlers to process requests. The server supports:

- **Path-based Routing**: Define endpoints with path parameters and handle requests dynamically.
- **Static File Serving**: Serve static files from a specified directory with automatic content type detection.
- **Filters**: Apply filters to requests for pre-processing or access control.
- Fairly open

## Web Sockets

The `TinyWeb.SocketServer` class provides WebSocket support, enabling real-time, bidirectional communication between 
the server and clients. Key features include:

- **Message Handling**: Register handlers for specific WebSocket paths to process incoming messages.
- **Secure Communication**: Supports WebSocket handshake and message framing for secure data exchange.
- **Integration with HTTP Server**: Seamlessly integrate WebSocket functionality with the HTTP server for a unified application architecture.

`TinyWeb.Server` and `TinyWeb.SocketServer` together are ideal for building lightweight web applications and services 
that require both HTTP and WebSocket 
capabilities. TinyWeb.SocketServer can be run separately.

## Rationale

I wanted to make something that:

1. Had nested `path( .. )` lambda functions to group endpoints together. This approach allows for a clean and intuitive way to define complex routing structures within the server. By nesting `path()` functions, developers can easily manage and organize routes, making the codebase more maintainable and scalable.
2. Could maybe be in one source file and have no dependencies at all
3. Does not log, and has not picked a logging framework, but laves that open as an implementation detail. I wrote much of https://cwiki.apache.org/confluence/display/avalon/AvalonNoLogging back in 2003 or so.

# Quick user guide

"Users" are developers, if that's not obvious. 

## Basic Use

### EndPoints

Here's a basic example of defining a GET endpoint using TinyWeb:

```java 
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    endPoint(TinyWeb.Method.GET, "/hello", (req, res, params) -> {
        res.write("Hello, World!");
        // req gives access to headers, etc
    });
}}.start();
```

In this example, a GET endpoint is defined at the path `/hello`. When a request is made to this endpoint, the server 
responds with "Hello, World!". The server is set to listen on port 8080.

### A Filter and an EndPoints

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

### Two EndPoints within a path

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

### A filter and an EndPoint within a path

Here's an example of using a filter to perform authentication and passing attributes to an endpoint within a path 
using TinyWeb:

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    path("/api", () -> {
        filter(TinyWeb.Method.GET, ".*", (req, res, params) -> {
            String allegedlyLoggedInCookie = req.getCookie("logged-in");
            // This test class only performs rot47 on the cookie passed in.
            // That's not secure in the slightest. See https://rot47.net/
            Authentication auth = IsEncryptedByUs.decrypt(allegedlyLoggedInCookie);
            if (!auth.authentic) {
                res.write("Try logging in again", 403);
                return false;
            } else {
                req.setAttribute("user", auth.user());
            }
            return true; // Continue processing
        });
        endPoint(TinyWeb.Method.GET, "/attribute-test", (req, res, params) -> {
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

#### Connecting to a WebSocket using TinyWeb.SocketClient

Here's an example of how to connect to a WebSocket using `TinyWeb.SocketClient`:

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

## Thoughts on WebSockets

### Short Messages with Follow-up GET Requests

One school of thought says WebSockets (and messaging systems generally) should send short notifications from the server 
to the client. These notifications can inform the client that an event has occurred or that new data is available. 
Instead of sending all the data over the implicitly async connection, the server sends a brief message, and the client 
then performs a traditional HTTP GET request to retrieve the full details.

This approach has several advantages:

1. **Efficiency**: By keeping WebSocket messages short, you reduce the amount of data transmitted over the WebSocket connection, which can be beneficial in bandwidth-constrained environments.
2. **Scalability**: Using HTTP GET requests for detailed data retrieval allows you to leverage existing HTTP caching and load balancing infrastructure, which can improve scalability and performance.
3. **Separation of Concerns**: This pattern separates the concerns of real-time notification and data retrieval, allowing each to be optimized independently.
4. **Security**: HTTP requests can be more easily secured and monitored than WebSocket messages, allowing for better control over data access and auditing.

Here's a simple example of how this might work:

1. **Server**: Sends a short message over the WebSocket, e.g., "{'newTrades': {'tickets': '['MSFT', 'since': 1730052329]}}".
2. **Client**: Receives the message and performs a GET request to `/api/trades/MSFT` to retrieve the full details.

This pattern is particularly useful in applications where real-time updates are needed, but the data associated with 
those updates is too large or complex to send over a WebSocket connection.

## Secure Channels

### Securing HTTP Channels

Currently, TinyWeb supports HTTP, which is suitable for development and testing environments. 
However, for production environments, it's crucial to secure HTTP channels using HTTPS. This can be achieved by 
fronting TinyWeb with a reverse proxy like Nginx or Apache, which can handle SSL/TLS termination. These proxies can be 
configured to forward requests to TinyWeb over HTTP, while serving clients over HTTPS. This approach leverages the 
robust SSL/TLS capabilities of these proxies, ensuring secure communication without modifying the TinyWeb server code.

### Securing WebSocket Channels

TinyWeb currently supports WebSocket (WS) connections, which are not encrypted. For secure communication, it's 
important to use Secure WebSocket (WSS) connections. Similar to HTTP, you can achieve this by using a reverse proxy 
that supports SSL/TLS termination for WebSockets. The proxy can handle the encryption and decryption of WebSocket 
traffic, forwarding it to TinyWeb over an unencrypted channel. This setup ensures that WebSocket communications are 
secure, protecting data from eavesdropping and tampering.

## Performance

Performance testing for `TinyWeb` has not been extensively conducted. However, due to its lightweight nature and minimal dependencies, `TinyWeb` is expected to perform efficiently for small to medium-sized applications. Here are some considerations for optimizing performance:

- **Use Virtual Threads**: `TinyWeb` leverages Java's virtual threads, which can handle a large number of concurrent connections efficiently. Ensure your Java version supports this feature.
- **Optimize Endpoints**: Minimize processing time within endpoints by avoiding heavy computations or blocking operations. Consider offloading such tasks to background threads or external services.
- **Static File Caching**: If serving static files, consider using a reverse proxy or CDN to cache and deliver these files, reducing the load on the server.
- **Connection Management**: Tune the server's connection settings, such as timeouts and maximum connections, to match your application's needs.

For detailed performance analysis, consider using profiling tools like JMH (Java Microbenchmark Harness) or integrating with monitoring solutions to track real-time performance metrics.

## Error handling

### In EndPoints Themselves

When handling requests in `TinyWeb`, it's important to understand how to set HTTP response codes and customize 
responses. HTTP response codes are crucial for indicating the result of a request to the client. Here's how you can 
manage responses in `TinyWeb`:

#### Setting HTTP Response Codes

In `TinyWeb`, you can set the HTTP response code by using the `write` method of the `Response` object. The `write` 
method allows you to specify both the response content and the status code. Here's an example:

```java
endPoint(TinyWeb.Method.GET, "/example", (req, res, params) -> {
    // Set a 200 OK response
    res.write("Request was successful", 200);
});
```

In that example, the endpoint responds with a 200 OK status code, indicating that the request was successful.

#### Common HTTP Response Codes

Understanding common HTTP response codes is essential for effectively communicating the outcome of requests:

- **200 OK**: The request was successful, and the server returned the requested resource.
- **201 Created**: The request was successful, and a new resource was created.
- **400 Bad Request**: The server could not understand the request due to invalid syntax.
- **401 Unauthorized**: The client must authenticate itself to get the requested response.
- **403 Forbidden**: The client does not have access rights to the content.
- **404 Not Found**: The server cannot find the requested resource.
- **500 Internal Server Error**: The server encountered an unexpected condition that prevented it from fulfilling the request.

#### Example: Handling Different Scenarios

Here's an example of how you might handle different scenarios in an endpoint:

```java
endPoint(TinyWeb.Method.POST, "/submit", (req, res, params) -> {
    if (req.getBody().isEmpty()) {
        // Respond with 400 Bad Request if the body is empty
        res.write("Request body cannot be empty", 400);
    } else {
        // Process the request and respond with 201 Created
        res.write("Resource created successfully", 201);
    }
});
```

In this example, the endpoint checks if the request body is empty. If it is, it responds with a 400 Bad Request status 
code. Otherwise, it processes the request and responds with a 201 Created status code.

By understanding and using HTTP response codes effectively, you can provide clear and meaningful feedback to clients 
interacting with your `TinyWeb` server.

### TinyWeb's Overridable Exception Methods

In `TinyWeb`, exception handling is an important aspect of managing server and application errors. The framework 
provides two overridable methods to handle exceptions: `serverException(e)` and `appHandlingException(Exception e)`. 
These methods allow you to customize how exceptions are logged or processed.

#### serverException(e)

The `serverException(e)` method is called when a `ServerException` occurs. This typically involves issues related to 
the server's internal operations, such as network errors or configuration problems. By default, this method logs the 
exception message and stack trace to the standard error stream. You can override this method to implement custom 
logging or error handling strategies.

Example:

```java
// As you instantiate TinyWeb.Server, you would add an override:
@Override
protected void serverException(ServerException e) {
    // Custom logging logic
    System.err.println("Custom Server Exception: " + e.getMessage());
    e.printStackTrace(System.err);
}
```

#### appHandlingException(Exception e)

The `appHandlingException(Exception e)` method is invoked when an exception occurs within an endpoint or filter. 
This is useful for handling application-specific errors, such as invalid input or business logic failures. That would
nearly always be an endpoint of filter throwing `java.lang.RuntimeException` or `java.lang.Error`. They are not supposed
to, but they may do so. By default, this method logs the exception message and stack trace to the standard error 
stream. You can override it to provide custom error handling, such as sending alerts or writing to a log file.

Example:

```java
// As you instantiate TinyWeb.Server, you would add an override:
@Override
protected void appHandlingException(Exception e) {
    // Custom application error handling
    System.err.println("Custom Application Exception: " + e.getMessage());
    e.printStackTrace(System.err);
}
```

By overriding these methods, you can tailor the exception handling behavior of your `TinyWeb` server to meet your 
application's specific needs, ensuring that errors are managed effectively and transparently.

### Input Validation

Input validation is crucial for ensuring the security and reliability of your `TinyWeb` application. While `TinyWeb` 
does not provide built-in input validation, developers should implement their own validation logic to protect against 
common vulnerabilities such as XSS (Cross-Site Scripting) and CSRF (Cross-Site Request Forgery).

#### Example: Basic Input Validation

Here's an example of how you might implement basic input validation in an endpoint:

```java
endPoint(TinyWeb.Method.POST, "/submit", (req, res, params) -> {
    String input = req.getBody();
    
    // Simple validation: check if input is not empty and does not contain malicious scripts
    if (input == null || input.trim().isEmpty() || input.contains("<script>")) {
        res.write("Invalid input", 400);
        return;
    }
    
    // Proceed with processing the valid input
    res.write("Input accepted", 200);
});
```

#### Security Best Practices

- **Sanitize Inputs**: Remove or escape potentially harmful characters from user inputs.
- **Use CSRF Tokens**: Implement CSRF protection by requiring tokens for state-changing requests.
- **Validate on Both Client and Server**: Perform validation on the client side for user feedback and on the server side for security.
- **Limit Input Size**: Restrict the size of inputs to prevent buffer overflow attacks.

By implementing robust input validation, you can enhance the security of your `TinyWeb` application and protect it 
from common web vulnerabilities.

## Integrating other frameworks

### Dependency Injection

We can't automate dependency injection with TinyWeb. The reason for that is handlers don't take strongly typed 
dependencies in the (req, resp, context) functional interfaces, nor do they have constructors associated. The nested
grammar is felt to me more important than making the tech directly compatible with DI. We can follow Inversion of 
Control (IoC) with a lookup-style container, and we've built something rudimentary in. Here's an example

```java
public static class ProductInventory {

    Map<String, Integer> stockItems = new HashMap<>() {{
        put("apple", 100);
        put("orange", 50);
        put("bagged banana", 33);
    }};

    public boolean customerReserves(String item) {
        if (stockItems.containsKey(item)) {
            if (stockItems.get(item) > 0) {
                stockItems.put(item, stockItems.get(item) - 1);
                return true;
            }
        }
        return false;
    }
}

public static class ShoppingCart {

    private final ProductInventory inv;
    private final Map<String, Integer> items = new HashMap<>();

    public ShoppingCart(ProductInventory inv) {
        this.inv = inv;
    }

    public int cartCount() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean pickItem(String item) {
        boolean gotIt = inv.customerReserves(item);
        if (!gotIt) {
            return false;
        }
        items.put(item, items.getOrDefault(item, 0) + 1);
        return true;
    }
}

// Also copy in `and endpoint and filters can depend on components`

```


### ORM Technologies

Object-Relational Mapping (ORM) is a technique that allows developers to interact with a database using objects, 
rather than writing raw SQL queries. JDBI is a lightweight ORM library that can be easily integrated with `TinyWeb` 
for database operations.

Here's a basic example of using JDBI with `TinyWeb`:

1. Add JDBI to your project dependencies (e.g., in `pom.xml` for Maven):

```xml
<dependency>
    <groupId>org.jdbi</groupId>
    <artifactId>jdbi3-core</artifactId>
    <version>3.25.0</version>
</dependency>
```

2. Use JDBI to interact with the database in your application. JDBI provides transaction management capabilities, 
3. allowing you to execute multiple operations within a single transaction. The JDBI instance is typically created 
4. at a global scope and shared across the application to manage database connections efficiently.

Here's an example of using JDBI with transaction management:

```java
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;

public class MyDatabaseApp {
    private final Jdbi jdbi;

    public MyDatabaseApp() {
        this.jdbi = Jdbi.create("jdbc:h2:mem:test");
    }

    public void start() {
        TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
            endPoint(TinyWeb.Method.GET, "/users", (req, res, params) -> {
                List<String> users = jdbi.inTransaction(handle -> 
                    handle.createQuery("SELECT name FROM users")
                          .mapTo(String.class)
                          .list());
                res.write(String.join(", ", users));
            });

            endPoint(TinyWeb.Method.POST, "/addUser", (req, res, params) -> {
                jdbi.useTransaction(TransactionIsolationLevel.SERIALIZABLE, handle -> {
                    handle.execute("INSERT INTO users (name) VALUES (?)", req.getBody());
                });
                res.write("User added successfully", 201);
            });
        }}.start();
    }
}
```

In this example, the JDBI instance is used to manage transactions for both reading and writing operations. The 
`inTransaction` method is used to execute a query within a transaction, and the `useTransaction` method is used 
to perform an insert operation with a specified transaction isolation level.

```java
import org.jdbi.v3.core.Jdbi;

public class MyDatabaseApp {
    private final Jdbi jdbi;

    public MyDatabaseApp() {
        this.jdbi = Jdbi.create("jdbc:h2:mem:test");
    }

    public void start() {
        TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
            endPoint(TinyWeb.Method.GET, "/users", (req, res, params) -> {
                List<String> users = jdbi.withHandle(handle ->
                    handle.createQuery("SELECT name FROM users")
                          .mapTo(String.class)
                          .list());
                res.write(String.join(", ", users));
            });
        }}.start();
    }
}
```

In this example, JDBI is used to query a list of users from an H2 in-memory database and return the results via 
a `TinyWeb` endpoint.

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

## Testing Your Web App

Testing is a critical part of developing reliable web applications. `TinyWeb` can be tested using various frameworks, 
each offering unique features. Here are some suggestions:

### Cuppa-Framework

The Cuppa-Framework is recommended for testing `TinyWeb` applications due to its idiomatic style, which closely aligns 
with `TinyWeb`'s composition approach. It allows for expressive and readable test definitions.

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

### JUnit and TestNG

JUnit and TestNG are popular testing frameworks that provide extensive features for unit and integration testing. They 
are well-suited for testing individual components and overall application behavior.

Example with JUnit:

```java
import org.junit.Test;
import static org.junit.Assert.*;

public class MyWebAppTest {
    @Test
    public void testGreetingEndpoint() {
        // Test logic here
    }
}
```

### Mockito

Mockito is a powerful mocking framework that can be used to create mock objects for testing. It is particularly useful 
for isolating components and testing interactions.

Example with Mockito:

```java
import static org.mockito.Mockito.*;

public class MyWebAppTest {
    @Test
    public void testWithMock() {
        MyService mockService = mock(MyService.class);
        when(mockService.getData()).thenReturn("Mock Data");
        
        // Test logic using mockService
    }
}
```

By leveraging these testing frameworks, you can ensure that your `TinyWeb` application is robust, reliable, and ready 
for production.

# Build and Test of TinyWeb itself

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

TinyWeb$ServerContext.class  TinyWeb$Filter.class  TinyWeb$PathContext.class  
TinyWeb$Server.class  TinyWeb$SocketServer$SocketMessageHandler.class 
TinyWeb$EndPoint.class  TinyWeb$FilterEntry.class  TinyWeb$Request.class  
TinyWeb$ServerException.class  TinyWeb$SocketServer.class  TinyWeb.class 
TinyWeb$MessageSender.class  TinyWeb$Response.class  TinyWeb$SocketClient.class      
TinyWeb$Method.class  TinyWeb$Server$1.class  TinyWeb$JavascriptSocketClient.class 
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

## TinyWeb's own test results

As mentioned, Cuppa-Framework is the tech used for testing, and it outputs spec-style success/failure like so, 
from `TinyWebTest.java`, and part of the tested code is from `TinyWeb.ExampleApp` including the 
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

ChatGPT estimates the path coverage for the TinyWeb class to be around 85-90%

I wish I could use Cuppa to generate example code in markdown, too. Maybe I'll raise that feature request.
