import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServer {
    public enum Method { GET, POST, PUT, DELETE }

    private final HttpServer server;
    private final Map<Method, Map<Pattern, Handler>> routes = new HashMap<>();

    @FunctionalInterface
    public interface Handler {
        void handle(Request request, Response response, Map<String, String> pathParams) throws IOException;
    }

    public static class Request {
        private final HttpExchange exchange;
        private final String body;

        public Request(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
            this.body = new String(exchange.getRequestBody().readAllBytes());
        }

        public String getBody() { return body; }
        public Map<String, List<String>> getHeaders() { return exchange.getRequestHeaders(); }
        public String getPath() { return exchange.getRequestURI().getPath(); }
        public String getQuery() { return exchange.getRequestURI().getQuery(); }
    }

    public static class Response {
        private final HttpExchange exchange;

        public Response(HttpExchange exchange) {
            this.exchange = exchange;
        }

        public void write(String content) throws IOException {
            write(content, 200);
        }

        public void write(String content, int statusCode) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            byte[] bytes = content.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }
    }

    public WebServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        for (Method method : Method.values()) {
            routes.put(method, new HashMap<>());
        }

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Method method = Method.valueOf(exchange.getRequestMethod());

            Map<Pattern, Handler> methodRoutes = routes.get(method);
            if (methodRoutes == null) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            for (Map.Entry<Pattern, Handler> route : methodRoutes.entrySet()) {
                Matcher matcher = route.getKey().matcher(path);
                if (matcher.matches()) {
                    Map<String, String> params = new HashMap<>();
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        params.put(String.valueOf(i), matcher.group(i));
                    }

                    try {
                        route.getValue().handle(
                                new Request(exchange),
                                new Response(exchange),
                                params
                        );
                    } catch (Exception e) {
                        sendError(exchange, 500, "Internal server error: " + e.getMessage());
                    }
                    return;
                }
            }

            sendError(exchange, 404, "Not found");
        });
    }

    public WebServer path(String basePath, Runnable routes) {
        routes.run();
        return this;
    }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = message.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    public WebServer handle(Method method, String path, Handler handler) {
        routes.get(method).put(Pattern.compile("^" + path + "$"), handler);
        return this;
    }

    public void start() {
        server.start();
    }

    public static void main(String[] args) throws IOException {
        new WebServer(8080) {{

            path("/foo", () -> handle(Method.GET, "/foo/bar", (req, res, params) -> {
                res.write("Hello, World!");
            }));
                handle(Method.GET, "/bar", (req, res, params) -> {
                    res.write("Hello, World!");
                });
            });

            handle(Method.GET, "/users/(\\w+)", (req, res, params) -> {
                res.write("User profile: " + params.get("1"));
            });

            handle(Method.POST, "/echo", (req, res, params) -> {
                res.write("You sent: " + req.getBody());
            });

            handle(Method.GET, "/greeting/(\\w+)/(\\w+)", (req, res, params) -> {
                res.write(String.format("Hello, %s %s!",
                        params.get("1"),
                        params.get("2")
                ));
            });

            start();

            System.out.println("Server running on port 8080");
        }};
    }
}
