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

public class WebServer {
    public enum Method { GET, POST, PUT, DELETE }

    private final HttpServer server;
    private Map<Method, Map<Pattern, Handler>> routes = new HashMap<>();
    private Map<Method, List<FilterEntry>> filters = new HashMap<>();

    @FunctionalInterface
    public interface Handler {
        void handle(Request request, Response response, Map<String, String> pathParams);
    }

    public record SimulatedResponse(String body, int statusCode, String contentType, Map<String, List<String>> headers) {}

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
        System.out.println("not matched2 " + path);
        return new SimulatedResponse("Not found", 404, "text/plain", Collections.emptyMap());
    }

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
        public String getQuery() { return exchange.getRequestURI().getQuery(); }
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
            blah(content, statusCode, exchange);
        }

        private static void blah(String content, int statusCode, HttpExchange exchange) {
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

    public WebServer(int port) {
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
                System.out.println("not matched2 " + path);

                sendError(exchange, 404, "Not found");
            }
        });
    }

    public WebServer path(String basePath, Runnable routes) {
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

        return this;
    }


    private void sendError(HttpExchange exchange, int code, String message) {
        Response.blah(message, code, exchange);
    }

    public WebServer handle(WebServer.Method method, String path, WebServer.Handler handler) {
        routes.computeIfAbsent(method, k -> new HashMap<>())
              .put(Pattern.compile("^" + path + "$"), handler);
        return this;
    }

    public WebServer filter(WebServer.Method method, String path, WebServer.Filter filter) {
        filters.computeIfAbsent(method, k -> new ArrayList<>())
                .add(new FilterEntry(Pattern.compile("^" + path + "$"), filter));
        return this;
    }

    public WebServer serveStaticFiles(String basePath, String directory) {
        System.out.println("s regn " + basePath + "/(.*)");
        handle(Method.GET, basePath + "/(.*)", (req, res, params) -> {
            String filePath = params.get("1");
            Path path = Paths.get(directory, filePath);
            System.out.println(path.toString());
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
                System.out.println("not matched1 " + path);
                sendError(res.exchange, 404, "File not found");
            }
        });
        return this;
    }
    public WebServer start() {
        server.start();
        System.out.println("svr started");
        return this;
    }

    public WebServer stop() {
        server.stop(0);
        System.out.println("svr stopped");
        return this;
    }

    /*
     * An inline example of composing (Tiny) WebServer.
     * If anyone was using this tech for their own solution they would
     * not use these two methods, even if they were inspired by them.
     */
    public static void main(String[] args) {
        exampleComposition(args, new ExampleApp()).start();
    }
    public static WebServer exampleComposition(String[] args, ExampleApp app) {
        return new WebServer(8080) {{

            path("/foo", () -> {
                filter(Method.GET, "/.*", (req, res, params) -> {
                    System.out.println("filter");
                    boolean proceed = new Random().nextBoolean();
                    if (!proceed) {
                        res.write("Access Denied", 403);
                    }
                    return proceed;
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

        }};
    }

    public static class ExampleApp {
        public void foobar(Request req, Response res, Map<String, String> params) {
            res.write(String.format("Hello, %s %s!", params.get("1"), params.get("2")));
        }
    }
}
