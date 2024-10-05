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

    @FunctionalInterface
    public interface Filter {
        boolean filter(Request request, Response response, Map<String, String> pathParams) throws IOException;
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

                    // Apply filters
                    for (Map.Entry<Pattern, Handler> filterEntry : routes.get(method).entrySet()) {
                        Matcher filterMatcher = filterEntry.getKey().matcher(path);
                        if (filterMatcher.matches()) {
                            Map<String, String> filterParams = new HashMap<>();
                            for (int i = 1; i <= filterMatcher.groupCount(); i++) {
                                filterParams.put(String.valueOf(i), filterMatcher.group(i));
                            }
                            try {
                                boolean proceed = ((Filter) filterEntry.getValue()).filter(
                                        new Request(exchange),
                                        new Response(exchange),
                                        filterParams
                                );
                                if (!proceed) {
                                    return; // Stop processing if filter returns false
                                }
                            } catch (Exception e) {
                                sendError(exchange, 500, "Internal server error: " + e.getMessage());
                                return;
                            }
                        }
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
        Runnable wrappedRoutes = () -> {
            Map<Method, Map<Pattern, Handler>> originalRoutes = new HashMap<>(this.routes);
            for (Method method : Method.values()) {
                if (!this.routes.containsKey(method)) {
                    this.routes.put(method, new HashMap<>());
                }
            }
            routes.run();
            for (Map.Entry<Method, Map<Pattern, Handler>> entry : this.routes.entrySet()) {
                Method method = entry.getKey();
                Map<Pattern, Handler> methodRoutes = entry.getValue();
                Iterator<Map.Entry<Pattern, Handler>> iterator = methodRoutes.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Pattern, Handler> route = iterator.next();
                    Pattern pattern = route.getKey();
                    Handler handler = route.getValue();
                    originalRoutes.get(method).put(Pattern.compile("^" + basePath + pattern.pattern().substring(1)), handler);
                }
            }
            this.routes.putAll(originalRoutes);
        };
        wrappedRoutes.run();
        return this;
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = message.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    public WebServer handle(WebServer.Method method, String path, WebServer.Handler handler) {
        routes.computeIfAbsent(method, k -> new HashMap<>())
              .put(Pattern.compile("^" + path + "$"), handler);
        return this;
    }

    public WebServer filter(WebServer.Method method, String path, WebServer.Filter filter) {
        routes.computeIfAbsent(method, k -> new HashMap<>())
              .put(Pattern.compile("^" + path + "$"), (req, res, params) -> {
                  if (!filter.filter(req, res, params)) {
                      return; // Stop processing if filter returns false
                  }
              });
        return this;
    }

    public void start() {
        server.start();
    }

    public static void main(String[] args) throws IOException {
        compose(args, new App()).start();
    }

    public static WebServer compose(String[] args, App app) throws IOException {
        return new WebServer(8080) {{

            path("/foo", () -> {
                filter(Method.GET, "/*", (req, res, params) -> {
                    System.out.println("filter");
                    return new Random().nextBoolean();
                    // 50% of requests to /foo/* should be vetoed (for testing of filters)
                });
                handle(Method.GET, "/bar", (req, res, params) -> {
                    res.write("Hello, World!");
                    // This endpoint is /foo/bar if that wasn't obvious
                });
            });

            handle(Method.GET, "/users/(\\w+)", (req, res, params) -> {
                res.write("User profile: " + params.get("1"));
            });

            handle(Method.POST, "/echo", (req, res, params) -> {
                res.write("You sent: " + req.getBody());
            });

            handle(Method.GET, "/greeting/(\\w+)/(\\w+)", app::foobar);

        }};
    }
    public static class App {

        public void foobar(Request req, Response res, Map<String, String> params) throws IOException {
            res.write(String.format("Hello, %s %s!", params.get("1"), params.get("2")));
        }
    }
}
