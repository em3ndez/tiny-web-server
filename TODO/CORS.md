# Implementing CORS in TinyWeb

To enable Cross-Origin Resource Sharing (CORS) in TinyWeb, follow these steps:

## Step 1: Add CORS Headers

Create a filter to add CORS headers to every response. This filter will ensure that the necessary headers are included in all responses, allowing cross-origin requests.

```java
public class CORSFilter implements TinyWeb.Filter {
    @Override
    public TinyWeb.FilterResult filter(TinyWeb.Request request, TinyWeb.Response response, TinyWeb.RequestContext ctx) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        return TinyWeb.FilterResult.CONTINUE;
    }
}
```

## Step 2: Handle Preflight Requests

Modify the server to handle OPTIONS requests, which are used for preflight checks in CORS. This involves responding with the appropriate CORS headers.

```java
public class CORSPreflightHandler implements TinyWeb.EndPoint {
    @Override
    public void handle(TinyWeb.Request request, TinyWeb.Response response, TinyWeb.RequestContext ctx) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.write("", 204); // No Content
    }
}
```

## Step 3: Integrate CORS Handling into TinyWeb

In your TinyWeb server setup, add the CORS filter and preflight handler to the server configuration.

```java
TinyWeb.Server server = new TinyWeb.Server(8080, -1) {{
    filter(".*", new CORSFilter());
    endPoint(TinyWeb.Method.OPTIONS, ".*", new CORSPreflightHandler());
    // Add other endpoints and filters here
}}.start();
```

By following these steps, you can implement CORS handling in your TinyWeb server, allowing it to handle cross-origin requests gracefully.
