import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class WebServer {
    public enum Method { GET, POST, PUT, DELETE }

    private final HttpServer server;
    private final Map<Method, Map<Pattern, Handler>> routes = new HashMap<>();
    private String pathPrefix = "";

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

    public class RouteGroup {
        private final String groupPrefix;
        private final String previousPrefix;

        private RouteGroup(String prefix) {
            this.groupPrefix = prefix;
            this.previousPrefix = pathPrefix;
            pathPrefix = previousPrefix + prefix;
        }

        public RouteGroup routes(Consumer<WebServer> routeDefinitions) {
            routeDefinitions.accept(WebServer.this);
            return this;
        }

        public void end() {
            pathPrefix = previousPrefix;
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

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = message.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    public WebServer addHandler(Method method, String path, Handler handler) {
        routes.get(method).put(Pattern.compile("^" + pathPrefix + path + "$"), handler);
        return this;
    }

    public RouteGroup path(String prefix) {
        return new RouteGroup(prefix);
    }

    public void start() {
        server.start();
    }

    public static class Path {
        private final WebServer server;

        public Path(WebServer server, Consumer<WebServer> routeDefinitions) {
            this.server = server;
            routeDefinitions.accept(server);
        }

        public Path path(String prefix, Consumer<Path> routeDefinitions) {
            RouteGroup group = server.path(prefix);
            routeDefinitions.accept(this);
            group.end();
            return this;
        }
    }

    public static void main(String[] args) throws IOException {
        WebServer server = new WebServer(8080);

        new Path(server, s -> {
            s.addHandler(Method.GET, "/", (req, res, params) -> res.write("Hello, World!"));

            new Path(s, users -> {
                users.path("/users", u -> {
                    u.addHandler(Method.GET, "", (req, res, params) -> res.write("List users"));
                    u.addHandler(Method.GET, "/(\\w+)", (req, res, params) -> res.write("User profile: " + params.get("1")));

                    u.path("/(\\w+)/posts", posts -> {
                        posts.addHandler(Method.GET, "", (req, res, params) -> res.write("Posts by user: " + params.get("1")));
                        posts.addHandler(Method.GET, "/(\\d+)", (req, res, params) -> res.write("Post " + params.get("2") + " by user: " + params.get("1")));
                    });
                });

                users.path("/api", api -> {
                    api.path("/v1", v1 -> {
                        v1.addHandler(Method.GET, "/status", (req, res, params) -> res.write("API v1 Status: OK"));
                    });

                    api.path("/v2", v2 -> {
                        v2.addHandler(Method.GET, "/status", (req, res, params) -> res.write("API v2 Status: OK"));
                    });
                });
            });
        });

        server.start();
        System.out.println("Server running on port 8080");
    }
}
