import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.Base64;

public class WebServer {
    public enum Method {GET, POST, PUT, DELETE}

    private final HttpServer server;
    private final Map<Method, Map<Pattern, RouteConfig>> routes = new HashMap<>();
    private String pathPrefix = "";
    private final List<Filter> currentFilters = new ArrayList<>();

    // Simple in-memory storage for demo purposes
    private final Set<String> loggedInUsers = new HashSet<>();
    private final Map<String, String> validTokens = new HashMap<>();

    @FunctionalInterface
    public interface Handler {
        void handle(Request request, Response response, Map<String, String> pathParams) throws IOException;
    }

    @FunctionalInterface
    public interface Filter {
        boolean doFilter(Request request, Response response, Map<String, String> pathParams) throws IOException;
    }

    private static class RouteConfig {
        final Handler handler;
        final List<Filter> filters;

        RouteConfig(Handler handler, List<Filter> filters) {
            this.handler = handler;
            this.filters = new ArrayList<>(filters);
        }
    }

    public static class Request {
        private final HttpExchange exchange;
        private final String body;
        private final Map<String, Object> attributes = new HashMap<>();

        public Request(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
            this.body = new String(exchange.getRequestBody().readAllBytes());
        }

        public String getBody() {
            return body;
        }

        public Map<String, List<String>> getHeaders() {
            return exchange.getRequestHeaders();
        }

        public String getPath() {
            return exchange.getRequestURI().getPath();
        }

        public String getQuery() {
            return exchange.getRequestURI().getQuery();
        }

        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        public Optional<String> getAuthHeader() {
            return exchange.getRequestHeaders()
                    .getOrDefault("Authorization", List.of())
                    .stream()
                    .findFirst();
        }
    }

    public static class Response {
        private final HttpExchange exchange;
        private boolean headersSent = false;

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
        private final List<Filter> previousFilters;

        private RouteGroup(String prefix) {
            this.groupPrefix = prefix;
            this.previousPrefix = pathPrefix;
            this.previousFilters = new ArrayList<>(currentFilters);
            pathPrefix = previousPrefix + prefix;
        }

        public RouteGroup routes(Consumer<WebServer> routeDefinitions) {
            routeDefinitions.accept(WebServer.this);
            return this;
        }

        public void end() {
            pathPrefix = previousPrefix;
            currentFilters.clear();
            currentFilters.addAll(previousFilters);
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

            Map<Pattern, RouteConfig> methodRoutes = routes.get(method);
            if (methodRoutes == null) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            for (Map.Entry<Pattern, RouteConfig> route : methodRoutes.entrySet()) {
                Matcher matcher = route.getKey().matcher(path);
                if (matcher.matches()) {
                    Map<String, String> params = new HashMap<>();
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        params.put(String.valueOf(i), matcher.group(i));
                    }

                    Request request = new Request(exchange);
                    Response response = new Response(exchange);

                    try {
                        boolean shouldContinue = route.getValue().filters.stream()
                                .allMatch(filter -> {
                                    try {
                                        return filter.doFilter(request, response, params);
                                    } catch (IOException e) {
                                        return false;
                                    }
                                });

                        if (shouldContinue) {
                            route.getValue().handler.handle(request, response, params);
                        }
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

    public WebServer filter(Filter filter) {
        currentFilters.add(filter);
        return this;
    }

    public WebServer handler(Method method, String path, Handler handler) {
        routes.get(method).put(
                Pattern.compile("^" + pathPrefix + path + "$"),
                new RouteConfig(handler, currentFilters)
        );
        return this;
    }

    public RouteGroup path(String prefix) {
        return new RouteGroup(prefix);
    }

    public void start() {
        server.start();
    }

    public static void main(String[] args) throws IOException {
        new App() {{
            // Root routes
            handler(Method.GET, "/", (req, res, params) -> handleRoot(req, res, params));

            // Login endpoint
            handler(Method.POST, "/login", (req, res, params) -> handleLogin(req, res, params));

            // User routes with MustBeLoggedIn filter
            path("/users") {{
                    filter((req, res, params) -> authenticateBasicAuth(req, res, params));

                    handler(Method.GET, "", (req, res, params) -> handleListUsers(req, res, params));

                    handler(Method.GET, "/(\\w+)", (req, res, params) -> handleGetUserProfile(req, res, params));

                    end();
            }};

            // API routes with MustHaveValidToken filter
            path("/api") {{
                    filter((req, res, params) -> authenticateToken(req, res, params));

                    path("/v1") {{
                        handler(Method.GET, "/status", (req, res, params) -> handleApiStatus(req, res, params));

                        end();
                    }};
                    end();
            }};

            start();

            System.out.println("Server running on port 8080");
        }};
    }

    private static class App extends WebServer {
        public App() throws IOException {
            super(8080);
        }

        protected void handleRoot(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("Hello, World!");
        }

        protected void handleLogin(Request req, Response res, Map<String, String> params) throws IOException {
            String[] credentials = new String(
                    Base64.getDecoder().decode(
                            req.getAuthHeader().orElse("").replace("Basic ", "")
                    )
            ).split(":");

            if (credentials.length == 2) {
                String username = credentials[0];
                String password = credentials[1];

                if (password.length() > 3) {
                    loggedInUsers.add(username);
                    String token = Base64.getEncoder().encodeToString(
                            (username + ":" + System.currentTimeMillis()).getBytes()
                    );
                    validTokens.put(token, username);
                    res.write("Token: " + token);
                    return;
                }
            }
            res.write("Invalid credentials", 401);
        }

        protected boolean authenticateBasicAuth(Request req, Response res, Map<String, String> params) throws IOException {
            String[] credentials = new String(
                    Base64.getDecoder().decode(
                            req.getAuthHeader().orElse("").replace("Basic ", "")
                    )
            ).split(":");

            if (credentials.length == 2 && loggedInUsers.contains(credentials[0])) {
                req.setAttribute("username", credentials[0]);
                return true;
            }
            res.write("Must be logged in", 401);
            return false;
        }

        protected void handleListUsers(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("List users - logged in as: " + req.getAttribute("username"));
        }

        protected void handleGetUserProfile(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("User profile: " + params.get("1") +
                    " (viewed by: " + req.getAttribute("username") + ")");
        }

        protected boolean authenticateToken(Request req, Response res, Map<String, String> params) throws IOException {
            String token = req.getAuthHeader().orElse("")
                    .replace("Bearer ", "");

            if (validTokens.containsKey(token)) {
                req.setAttribute("username", validTokens.get(token));
                return true;
            }
            res.write("Invalid token", 401);
            return false;
        }

        protected void handleApiStatus(Request req, Response res, Map<String, String> params) throws IOException {
            res.write("API v1 Status: OK (user: " + req.getAttribute("username") + ")");
        }
    }
}
