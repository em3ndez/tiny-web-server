package com.paulhammant.tinywebserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TinyWeb {

    public enum Method { GET, POST, PUT, DELETE }

    public static class Context {

        protected Map<Method, Map<Pattern, Handler>> routes = new HashMap<>();
        protected Map<Method, List<FilterEntry>> filters = new HashMap<>();

        public PathContext path(String basePath, Runnable routes) {
            // Save current routes and filters
            Map<Method, Map<Pattern, Handler>> previousRoutes = this.routes;
            Map<Method, List<FilterEntry>> previousFilters = this.filters;

            // Create new maps to collect routes and filters within this path
            this.routes = new HashMap<>();
            this.filters = new HashMap<>();

            // Initialize empty maps for all methods
            for (Method method : Method.values()) {
                this.routes.put(method, new HashMap<>());
                this.filters.put(method, new ArrayList<>());
            }

            // Run the routes Runnable, which will populate this.routes and this.filters
            routes.run();

            // Prefix basePath to routes
            for (Method method : Method.values()) {
                Map<Pattern, Handler> methodRoutes = this.routes.get(method);
                if (methodRoutes != null && !methodRoutes.isEmpty()) {
                    Map<Pattern, Handler> prefixedRoutes = new HashMap<>();
                    for (Map.Entry<Pattern, Handler> entry : methodRoutes.entrySet()) {
                        Pattern pattern = entry.getKey();
                        Handler handler = entry.getValue();
                        Pattern newPattern = Pattern.compile("^" + basePath + pattern.pattern().substring(1));
                        prefixedRoutes.put(newPattern, handler);
                    }
                    previousRoutes.get(method).putAll(prefixedRoutes);
                }
            }

            // Prefix basePath to filters
            for (Method method : Method.values()) {
                List<FilterEntry> methodFilters = this.filters.get(method);
                if (methodFilters != null && !methodFilters.isEmpty()) {
                    List<FilterEntry> prefixedFilters = new ArrayList<>();
                    for (FilterEntry filterEntry : methodFilters) {
                        Pattern pattern = filterEntry.pattern;
                        Filter filter = filterEntry.filter;
                        Pattern newPattern = Pattern.compile("^" + basePath + pattern.pattern().substring(1));
                        prefixedFilters.add(new FilterEntry(newPattern, filter));
                    }
                    // Ensure previousFilters has a non-null list for this method
                    List<FilterEntry> previousMethodFilters = previousFilters.get(method);
                    if (previousMethodFilters == null) {
                        previousMethodFilters = new ArrayList<>();
                        previousFilters.put(method, previousMethodFilters);
                    }
                    previousMethodFilters.addAll(prefixedFilters);
                }
            }

            // Restore routes and filters
            this.routes = previousRoutes;
            this.filters = previousFilters;

            return new PathContext(this, basePath);
        }

        public SimulatedResponse directRequest(Method method, String path, String body, Map<String, List<String>> headers) {
            Map<Pattern, Handler> methodRoutes = routes.get(method);
            if (methodRoutes == null) {
                System.out.println("Method not allowed");
                return new SimulatedResponse("Method not allowed", 405, "text/plain", Collections.emptyMap());
            }

            boolean routeMatched = false;

            final StringBuilder responseBody = new StringBuilder();
            final int[] responseCode = {200};
            final String[] contentType = {"text/plain"};
            final Map<String, List<String>> responseHeaders = new HashMap<>();

            for (var route : methodRoutes.entrySet()) {
                Matcher matcher = route.getKey().matcher(path);
                if (matcher.matches()) {
                    routeMatched = true;
                    Map<String, String> params = new HashMap<>();
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        params.put(String.valueOf(i), matcher.group(i));
                    }

                    // Create pseudo request and response
                    Request request = new Request(null) {
                        @Override
                        public Map<String, List<String>> getHeaders() { return headers; }
                        @Override
                        public String getBody() { return body; }
                        @Override
                        public String getPath() { return path; }
                    };

                    Response response = new Response(null) {
                        @Override
                        public void write(String content, int statusCode) {
                            responseBody.append(content);
                            responseCode[0] = statusCode;
                        }

                        @Override
                        public void write(String content) {
                            write(content, 200);
                        }

                        public void setHeader(String name, String value) {
                            responseHeaders.put(name, List.of(value));
                        }
                    };

                    // Apply filters
                    List<FilterEntry> methodFilters = filters.get(method);
                    if (methodFilters != null) {
                        for (FilterEntry filterEntry : methodFilters) {
                            Matcher filterMatcher = filterEntry.pattern.matcher(path);
                            if (filterMatcher.matches()) {
                                Map<String, String> filterParams = new HashMap<>();
                                for (int i = 1; i <= filterMatcher.groupCount(); i++) {
                                    filterParams.put(String.valueOf(i), filterMatcher.group(i));
                                }
                                boolean proceed = filterEntry.filter.filter(request, response, filterParams);
                                if (!proceed) {
                                    return new SimulatedResponse(responseBody.toString(), responseCode[0], contentType[0], responseHeaders);
                                }
                            }
                        }
                    }

                    // Handle the request
                    route.getValue().handle(request, response, params);
                    return new SimulatedResponse(responseBody.toString(), responseCode[0], contentType[0], responseHeaders);
                }
            }
            return new SimulatedResponse("Not found", 404, "text/plain", Collections.emptyMap());
        }

        protected void sendError(HttpExchange exchange, int code, String message) {
            Response.sendResponse(message, code, exchange);
        }

        public Context handle(TinyWeb.Method method, String path, TinyWeb.Handler handler) {
            routes.computeIfAbsent(method, k -> new HashMap<>())
                    .put(Pattern.compile("^" + path + "$"), handler);
            return this;
        }

        public Context filter(TinyWeb.Method method, String path, TinyWeb.Filter filter) {
            filters.computeIfAbsent(method, k -> new ArrayList<>())
                    .add(new FilterEntry(Pattern.compile("^" + path + "$"), filter));
            return this;
        }

        public Context serveStaticFiles(String basePath, String directory) {
            handle(Method.GET, basePath + "/(.*)", (req, res, params) -> {
                String filePath = params.get("1");
                Path path = Paths.get(directory, filePath);
                if (Files.exists(path) && !Files.isDirectory(path)) {
                    try {
                        String contentType = Files.probeContentType(path);
                        res.exchange.getResponseHeaders().set("Content-Type", contentType != null ? contentType : "application/octet-stream");
                        byte[] fileBytes = Files.readAllBytes(path);
                        res.exchange.sendResponseHeaders(200, fileBytes.length);
                        res.exchange.getResponseBody().write(fileBytes);
                        res.exchange.getResponseBody().close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    sendError(res.exchange, 404, "File not found");
                }
            });
            return this;
        }

    }

    public static class PathContext extends Context {

        private final Context context;
        private final String basePath;

        public PathContext(Context context, String basePath) {

            this.context = context;
            this.basePath = basePath;
        }
    }

    public static class Server extends Context {

        private final HttpServer server;


        public Server(int port) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            for (Method method : Method.values()) {
                routes.put(method, new HashMap<>());
                filters.put(method, new ArrayList<>());
            }

            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                Method method = Method.valueOf(exchange.getRequestMethod());

                Map<Pattern, Handler> methodRoutes = routes.get(method);
                if (methodRoutes == null) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                boolean routeMatched = false;

                for (Map.Entry<Pattern, Handler> route : methodRoutes.entrySet()) {
                    Matcher matcher = route.getKey().matcher(path);
                    if (matcher.matches()) {
                        routeMatched = true;
                        Map<String, String> params = new HashMap<>();
                        for (int i = 1; i <= matcher.groupCount(); i++) {
                            params.put(String.valueOf(i), matcher.group(i));
                        }

                        // Apply filters
                        List<FilterEntry> methodFilters = filters.get(method);
                        if (methodFilters != null) {
                            for (FilterEntry filterEntry : methodFilters) {
                                Matcher filterMatcher = filterEntry.pattern.matcher(path);
                                if (filterMatcher.matches()) {
                                    Map<String, String> filterParams = new HashMap<>();
                                    for (int i = 1; i <= filterMatcher.groupCount(); i++) {
                                        filterParams.put(String.valueOf(i), filterMatcher.group(i));
                                    }
                                    try {
                                        boolean proceed = filterEntry.filter.filter(
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

                if (!routeMatched) {
                    sendError(exchange, 404, "Not found");
                }
            });
        }


        public TinyWeb.Server start() {
            server.start();
            return this;
        }

        public TinyWeb.Server stop() {
            server.stop(0);
            return this;
        }

    }


    @FunctionalInterface
    public interface Handler {
        void handle(Request request, Response response, Map<String, String> pathParams);
    }

    public record SimulatedResponse(String body, int statusCode, String contentType, Map<String, List<String>> headers) {}


    @FunctionalInterface
    public interface Filter {
        boolean filter(Request request, Response response, Map<String, String> pathParams);
    }

    public static class FilterEntry {
        public final Pattern pattern;
        public final Filter filter;

        public FilterEntry(Pattern pattern, Filter filter) {
            this.pattern = pattern;
            this.filter = filter;
        }
    }

    public static class Request {
        private final HttpExchange exchange;
        private final String body;

        public Request(HttpExchange exchange) {
            this.exchange = exchange;
            if (exchange != null) {
                try {
                    this.body = new String(exchange.getRequestBody().readAllBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                this.body = null;
            }
        }

        public String getBody() { return body; }
        public Map<String, List<String>> getHeaders() { return exchange.getRequestHeaders(); }
        public String getPath() { return exchange.getRequestURI().getPath(); }
        public String getQuery() {
            if (exchange != null) {
                return exchange.getRequestURI().getQuery();
            }
            return null;
        }

        public Map<String, String> getQueryParams() {
            String query = getQuery();
            if (query == null || query.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> queryParams = new HashMap<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                }
            }
            return queryParams;
        }
    }

    public static class Response {
        private final HttpExchange exchange;

        public Response(HttpExchange exchange) {
            this.exchange = exchange;
        }

        public void write(String content) {
            write(content, 200);
        }

        public void write(String content, int statusCode) {
            sendResponse(content, statusCode, exchange);
        }

        private static void sendResponse(String content, int statusCode, HttpExchange exchange) {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            byte[] bytes = content.getBytes();
            try {
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }



    /*
     * An inline example of composing (Tiny) WebServer.
     * If anyone was using this tech for their own solution they would
     * not use these two methods, even if they were inspired by them.
     */
    public static void main(String[] args) {
        ExampleApp.exampleComposition(args, new ExampleApp()).start();
    }

    public static class ExampleApp {
        public void foobar(Request req, Response res, Map<String, String> params) {
            res.write(String.format("Hello, %s %s!", params.get("1"), params.get("2")));
        }

        public static TinyWeb.Server exampleComposition(String[] args, ExampleApp app) {
            return new TinyWeb.Server(8080) {{

                path("/foo", () -> {
                    filter(Method.GET, "/.*", (req, res, params) -> {
                        System.out.println("keys " + req.getHeaders().toString());
                        if (req.getHeaders().containsKey("sucks")) {
                            res.write("Access Denied", 403);
                        }
                        System.out.println("filter");
                        return true;
                    });
                    handle(Method.GET, "/bar", (req, res, params) -> {
                        res.write("Hello, World!");
                        // This endpoint is /foo/bar if that wasn't obvious
                    });
                });

                serveStaticFiles("/static", new File(".").getAbsolutePath());


                handle(Method.GET, "/users/(\\w+)", (req, res, params) -> {
                    res.write("User profile: " + params.get("1"));
                });

                handle(Method.POST, "/echo", (req, res, params) -> {
                    res.write("You sent: " + req.getBody());
                });

                handle(Method.GET, "/greeting/(\\w+)/(\\w+)", app::foobar);

                path("/api", () -> {
                    handle(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, params) -> {
                        res.write("Parameter: " + params.get("1"));
                    });
                });

            }};
        }
    }

}
