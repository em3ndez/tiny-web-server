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

        protected Map<Method, Map<Pattern, EndPoint>> routes = new HashMap<>();
        protected Map<Pattern, SocketServer.SocketMessageHandler> wsRoutes = new HashMap<>();
        protected Map<Method, List<FilterEntry>> filters = new HashMap<>();

        public PathContext path(String basePath, Runnable routes) {
            // Save current routes and filters
            Map<Method, Map<Pattern, EndPoint>> previousRoutes = this.routes;
            Map<Pattern, SocketServer.SocketMessageHandler> previousWsRoutes = this.wsRoutes;
            Map<Method, List<FilterEntry>> previousFilters = this.filters;

            // Create new maps to collect routes and filters within this path
            this.routes = new HashMap<>();
            this.wsRoutes = new HashMap<>();
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
                Map<Pattern, EndPoint> methodRoutes = this.routes.get(method);
                if (methodRoutes != null && !methodRoutes.isEmpty()) {
                    Map<Pattern, EndPoint> prefixedRoutes = new HashMap<>();
                    for (Map.Entry<Pattern, EndPoint> entry : methodRoutes.entrySet()) {
                        Pattern pattern = entry.getKey();
                        EndPoint endPoint = entry.getValue();
                        Pattern newPattern = Pattern.compile("^" + basePath + pattern.pattern().substring(1));
                        prefixedRoutes.put(newPattern, endPoint);
                    }
                    previousRoutes.get(method).putAll(prefixedRoutes);
                }
            }

            // Prefix basePath to WebSocket handlers
            for (Map.Entry<Pattern, SocketServer.SocketMessageHandler> entry : this.wsRoutes.entrySet()) {
                Pattern pattern = entry.getKey();
                SocketServer.SocketMessageHandler wsHandler = entry.getValue();
                Pattern newPattern = Pattern.compile("^" + basePath + pattern.pattern().substring(1));
                previousWsRoutes.put(newPattern, wsHandler);
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
            this.wsRoutes = previousWsRoutes;
            this.filters = previousFilters;

            return new PathContext(basePath, this);
        }

        protected void sendError(HttpExchange exchange, int code, String message) {
                new Response(exchange).write(message, code);
        }

        public Context endPoint(TinyWeb.Method method, String path, EndPoint endPoint) {
            routes.computeIfAbsent(method, k -> new HashMap<>())
                    .put(Pattern.compile("^" + path + "$"), endPoint);
            return this;
        }

        public Context webSocket(String path, SocketServer.SocketMessageHandler wsHandler) {
            wsRoutes.put(Pattern.compile("^" + path + "$"), wsHandler);
            return this;
        }


        public Context filter(TinyWeb.Method method, String path, TinyWeb.Filter filter) {
            filters.computeIfAbsent(method, k -> new ArrayList<>())
                    .add(new FilterEntry(Pattern.compile("^" + path + "$"), filter));
            return this;
        }

        public Context serveStaticFiles(String basePath, String directory) {
            endPoint(Method.GET, basePath + "/(.*)", (req, res, params) -> {
                String filePath = params.get("1");
                Path path = Paths.get(directory, filePath);
                if (Files.exists(path) && !Files.isDirectory(path)) {
                    try {
                        String contentType = Files.probeContentType(path);
                        if (contentType == null) {
                            contentType = "application/octet-stream";
                        }
                        res.setHeader("Content-Type", contentType);
                        byte[] fileBytes = Files.readAllBytes(path);
                        res.write(new String(fileBytes), 200);
                    } catch (IOException e) {
                        throw new ServerException("Internal Static File Serving error for " + path, e);
                    }
                } else {
                    sendError(res.exchange, 404, "Not found");
                }
            });
            return this;
        }
    }

    public static class PathContext extends Context {

        private final String basePath;
        private final TinyWeb.Context parentContext;

        public PathContext(String basePath, TinyWeb.Context parentContext) {
            this.basePath = basePath;
            this.parentContext = parentContext;
        }

        @Override
        public PathContext endPoint(TinyWeb.Method method, String path, EndPoint endPoint) {
            String fullPath = basePath + path;
            parentContext.routes.computeIfAbsent(method, k -> new HashMap<>())
                    .put(Pattern.compile("^" + fullPath + "$"), endPoint);
            return this;
        }

        @Override
        public PathContext filter(TinyWeb.Method method, String path, TinyWeb.Filter filter) {
            String fullPath = basePath + path;
            parentContext.filters.computeIfAbsent(method, k -> new ArrayList<>())
                    .add(new TinyWeb.FilterEntry(Pattern.compile("^" + fullPath + "$"), filter));
            return this;
        }
    }

    public static class ServerException extends RuntimeException {
        public ServerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Server extends Context {

        private final HttpServer httpServer;
        private final SocketServer socketServer;
        private Thread simpleWebSocketServerThread = null;

        public Server(int httpPort, int webSocketPort) {
            try {
                httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
            } catch (IOException e) {
                throw new ServerException("Can't listen on port " + httpPort, e);
            }
            httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            if (webSocketPort != -1) {
                socketServer = new SocketServer(webSocketPort) {
                    @Override
                    protected SocketMessageHandler getHandler(String path) {
                        for (Map.Entry<Pattern, SocketMessageHandler> patternWebSocketMessageHandlerEntry : wsRoutes.entrySet()) {
                            Pattern key = patternWebSocketMessageHandlerEntry.getKey();
                            SocketMessageHandler value = patternWebSocketMessageHandlerEntry.getValue();
                            if (key.matcher(path).matches()) {
                                return value;
                            }
                        }
                        return new SocketMessageHandler() {
                            @Override
                            public void handleMessage(byte[] message, MessageSender sender) throws IOException {
                                sender.sendTextFrame("no matching path on the server side".getBytes("UTF-8"));
                            }
                        };
                    }
                };
            } else {

                socketServer = null;
            }

            for (Method method : Method.values()) {
                routes.put(method, new HashMap<>());
                filters.put(method, new ArrayList<>());
            }

            httpServer.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                Method method = Method.valueOf(exchange.getRequestMethod());

                Map<Pattern, EndPoint> methodRoutes = routes.get(method);
                if (methodRoutes == null) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                boolean routeMatched = false;

                for (Map.Entry<Pattern, EndPoint> route : methodRoutes.entrySet()) {
                    Matcher matcher = route.getKey().matcher(path);
                    if (matcher.matches()) {
                        routeMatched = true;
                        Map<String, String> params = new HashMap<>();
                        int groupCount = matcher.groupCount();
                        Pattern key = route.getKey();
                        for (int i = 1; i <= groupCount; i++) {
//                            if (key.toString().endsWith("?(.*)$") && i == groupCount) {
//                                placeQueryStringItemsInParams(params, matcher.group(i));
//                            } else {
                                params.put(String.valueOf(i), matcher.group(i));
//                            }
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
                                        Request request = new Request(exchange);
                                        Response response = new Response(exchange);
                                        boolean proceed = false;
                                        try {
                                            proceed = filterEntry.filter.filter(request, response, filterParams);
                                        } catch (Exception e) {
                                            appHandlingException(e);
                                            sendError(exchange, 500, "Server Error");
                                            return;
                                        }
                                        if (!proceed) {
                                            return; // Stop processing if filter returns false
                                        }
                                    } catch (ServerException e) {
                                        serverException(e);
                                        sendError(exchange, 500, "Internal server error: " + e.getMessage());
                                        return;
                                    }
                                }
                            }
                        }

                        try {
                            Request request = new Request(exchange);
                            Response response = new Response(exchange);
                            try {
                                route.getValue().handle(request, response, params);
                            } catch (Exception e) {
                                appHandlingException(e);
                                sendError(exchange, 500, "Server error");
                                return;
                            }
                        } catch (ServerException e) {
                            serverException(e);
                            sendError(exchange, 500,"Internal server error: " + e.getMessage());
                            return;
                        }
                        return;
                    }
                }

                if (!routeMatched) {
                    sendError(exchange, 404, "Not found");
                }
            });
        }

        protected void serverException(ServerException e) {
            System.out.println(e.getMessage() + "\nStack Trace:");
            e.printStackTrace(System.out);
        }

        /**
         * Most likely a RuntimeException or Error in a endPoint() or filter() code block
         */
        protected void appHandlingException(Exception e) {
            System.out.println(e.getMessage() + "\nStack Trace:");
            e.printStackTrace(System.out);
        }

        public TinyWeb.Server start() {
            httpServer.start();
            if (socketServer != null) {
                simpleWebSocketServerThread = new Thread(socketServer::start);
                simpleWebSocketServerThread.start();
            }
            return this;
        }

        public TinyWeb.Server stop() {
            httpServer.stop(0);
            if (simpleWebSocketServerThread != null) {
                socketServer.stop();
                simpleWebSocketServerThread.interrupt();
            }
            return this;
        }
    }


    @FunctionalInterface
    public interface EndPoint {
        void handle(Request request, Response response, Map<String, String> pathParams);
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

        private final Map<String, String> queryParams;

        public Request(HttpExchange exchange) {
            this.exchange = exchange;
            if (exchange != null) {
                try {
                    this.body = new String(exchange.getRequestBody().readAllBytes());
                    this.queryParams = parseQueryParams(exchange.getRequestURI().getQuery());

                } catch (IOException e) {
                    throw new ServerException("Internal request error, for " + exchange.getRequestURI(), e);
                }
            } else {
                this.body = null;
                this.queryParams = null;
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

        protected Map<String, String> parseQueryParams(String query) {
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


        // TODO: NEEDED
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

        protected final HttpExchange exchange;

        public Response(HttpExchange exchange) {
            this.exchange = exchange;
        }

        public void write(String content) {
            write(content, 200);
        }

        public void write(String content, int statusCode) {
            sendResponse(content, statusCode);
        }

        public void setHeader(String name, String value) {
            exchange.getResponseHeaders().set(name, value);
        }



        protected void sendResponse(String content, int statusCode) {
            byte[] bytes = content.getBytes();
            try {
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            } catch (IOException e) {
                throw new ServerException("Internal response error, for " + exchange.getRequestURI(), e);
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
            TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{

                path("/foo", () -> {
                    filter(Method.GET, "/.*", (req, res, params) -> {
                        if (req.getHeaders().containsKey("sucks")) {
                            res.write("Access Denied", 403);
                            return false; // don't proceed
                        }
                        return true; // proceed
                    });
                    endPoint(Method.GET, "/bar", (req, res, params) -> {
                        res.write("Hello, World!");
                        // This endpoint is /foo/bar if that wasn't obvious
                    });
                    webSocket("/eee", (message, sender) -> {
                        for (int i = 1; i <= 3; i++) {
                            String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                            sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                    });
                });

                serveStaticFiles("/static", new File(".").getAbsolutePath());

                endPoint(Method.GET, "/users/(\\w+)", (req, res, params) -> {
                    res.write("User profile: " + params.get("1"));
                });

                endPoint(Method.POST, "/echo", (req, res, params) -> {
                    res.write("You sent: " + req.getBody(), 201);
                });

                endPoint(Method.GET, "/greeting/(\\w+)/(\\w+)", app::foobar);

                endPoint(Method.PUT, "/update", (req, res, params) -> {
                    res.write("Updated data: " + req.getBody(), 200);
                });

                path("/api", () -> {
                    endPoint(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, params) -> {
                        res.write("Parameter: " + params.get("1"));
                    });
                });

            }};


            return server;
        }
    }

}
