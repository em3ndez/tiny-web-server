/*
 *
 * Tiny WebServer and WebSocketServer
 *
 * MIT License
 *
 * Copyright (c) Paul Hammant, 2024
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.paulhammant.tiny;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tiny {

    public static final String VERSION = "1.0-SNAPSHOT";
    public static final String SHA256_OF_SOURCE_LINES = "4db95effe627428070ba924fba5b6338d1cbcf7dcd78075dde59754719208a20"; // this line not included in SHA256 calc

    /* ==========================
     * Enums
     * ==========================
     */
    public enum HttpMethods { ALL, GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, CONNECT, TRACE, LINK, UNLINK, LOCK,
        UNLOCK, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, REPORT, SEARCH, PURGE, REBIND, UNBIND, ACL}

    public enum FilterAction {
        CONTINUE, STOP
    }

    /* ==========================
     * Interfaces
     * ==========================
     */

    public interface WebServerContext {
        PathContext path(String basePath, Runnable runnable);
        WebServerContext endPoint(HttpMethods method, String path, EndPoint endPoint);
        WebServerContext webSocket(String path, WebSocketMessageHandler wsHandler);
        WebServerContext filter(HttpMethods method, String path, Filter filter);
        WebServerContext filter(String path, Filter filter);
        WebServerContext serveStaticFilesAsync(String basePath, String directory);
        void sendErrorResponse(HttpExchange exchange, int code, String message);
    }

    public interface RequestContext {
        String getParam(String key);
        @SuppressWarnings("unchecked")
        <T> T dep(Class<T> clazz);

        void setAttribute(String key, Object value);
        Object getAttribute(String key);

    }

    @FunctionalInterface
    public interface EndPoint {
        void handle(Request request, Response response, RequestContext ctx);
    }

    @FunctionalInterface
    public interface Filter {
        FilterAction filter(Request request, Response response, RequestContext ctx);
    }

    @FunctionalInterface
    public interface WebSocketMessageHandler {
        void handleMessage(byte[] message, com.paulhammant.tiny.Tiny.MessageSender sender, RequestContext ctx);
    }

    @FunctionalInterface
    public interface InterruptibleConsumer<T> {
        boolean accept(T t);
    }


    /* ==========================
     * Core Classes
     * ==========================
     */
    public static class ServerState {
        private boolean hasStarted;
        public boolean hasStarted() {
            return hasStarted;
            this.connectionTimeoutMs = connectionTimeoutMs;
            this.serverOperationTimeoutMs = serverOperationTimeoutMs;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public int getServerOperationTimeoutMs() {
            return serverOperationTimeoutMs;
        }

        public void start() {
            hasStarted = true;
        }
    }

    public static class DependencyManager {
        protected final ComponentCache cache;

        public DependencyManager(ComponentCache cache) {
            if (cache instanceof UseOnceComponentCache) {
                this.cache = ((UseOnceComponentCache) cache).getHidden();
            } else {
                this.cache = cache;
            }
        }

        public <T> T instantiateDep(Class<T> clazz, ComponentCache requestCache, Matcher matcher) {
            // Implement logic to instantiate or retrieve the dependency
            // For example, using the cache to manage instances
            return requestCache.getOrCreate(clazz, () -> {
                // Add instantiation logic here
                throw new com.paulhammant.tiny.Tiny.DependencyException(clazz);
            });
        }
    }

    public static abstract class AbstractWebServerContext implements WebServerContext {

        protected Map<HttpMethods, Map<Pattern, EndPoint>> endPoints = new HashMap<>();
        protected Map<Pattern, WebSocketMessageHandler> wsEndPoints = new HashMap<>();
        protected Map<HttpMethods, List<FilterEntry>> filters = new HashMap<>() {{ put(HttpMethods.ALL, new ArrayList<>()); }};
        protected final ServerState serverState;

        public AbstractWebServerContext(ServerState serverState) {
            this.serverState = serverState;
        }

        public PathContext path(String basePath, Runnable runnable) {
            // Save current endpoints and filters
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add paths after the server has started.");
            }
            // Check if the path is already registered
            for (HttpMethods method : HttpMethods.values()) {
                Map<Pattern, EndPoint> methodEndPoints = this.endPoints.get(method);
                if (methodEndPoints != null) {
                    for (Pattern pattern : methodEndPoints.keySet()) {
                        if (pattern.pattern().startsWith("^" + basePath + "/")) {
                            throw new IllegalStateException("Path already registered: " + basePath);
                        }
                    }
                }
            }

            Map<HttpMethods, Map<Pattern, EndPoint>> previousEndPoints = this.endPoints;
            Map<Pattern, WebSocketMessageHandler> previousWsEndPoints = this.wsEndPoints;
            Map<HttpMethods, List<FilterEntry>> previousFilters = this.filters;

            // Create new maps to collect endpoints and filters within this path
            this.endPoints = new HashMap<>();
            this.wsEndPoints = new HashMap<>();
            this.filters = new HashMap<>();

            // Initialize empty maps for all methods
            for (HttpMethods method : HttpMethods.values()) {
                this.endPoints.put(method, new HashMap<>());
                this.filters.put(method, new ArrayList<>());
            }


            // Run the Runnable, which will populate this.endpoints and this.filters
            runnable.run();

            // Prefix basePath to endpoints
            for (HttpMethods method : HttpMethods.values()) {
                Map<Pattern, EndPoint> methodEndPoints = this.endPoints.get(method);
                if (methodEndPoints != null && !methodEndPoints.isEmpty()) {
                    Map<Pattern, EndPoint> prefixedEndPoints = new HashMap<>();
                    for (Map.Entry<Pattern, EndPoint> entry : methodEndPoints.entrySet()) {
                        Pattern pattern = entry.getKey();
                        EndPoint endPoint = entry.getValue();
                        Pattern newPattern = Pattern.compile("^" + basePath + pattern.pattern().substring(1));
                        prefixedEndPoints.put(newPattern, endPoint);
                    }
                    previousEndPoints.get(method).putAll(prefixedEndPoints);
                }

            }

            // Prefix basePath to WebSocket handlers
            for (Map.Entry<Pattern, WebSocketMessageHandler> entry : this.wsEndPoints.entrySet()) {
                Pattern pattern = entry.getKey();
                WebSocketMessageHandler wsHandler = entry.getValue();
                Pattern newPattern = Pattern.compile("^" + basePath + pattern.pattern().substring(1));
                previousWsEndPoints.put(newPattern, wsHandler);
            }

            // Prefix basePath to filters
            for (HttpMethods method : HttpMethods.values()) {
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

            // Restore endpoints and filters
            this.endPoints = previousEndPoints;
            this.wsEndPoints = previousWsEndPoints;
            this.filters = previousFilters;
            return new PathContext(serverState);
        }

        // TODO was protected - could be static?
        public void sendErrorResponse(HttpExchange exchange, int code, String message) {
                new Response(exchange).write(message, code);
        }

        public WebServerContext endPoint(HttpMethods method, String path, EndPoint endPoint) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add endpoints after the server has started.");
            }
            endPoints.computeIfAbsent(method, k -> new HashMap<>())
                    .put(Pattern.compile("^" + path + "$"), endPoint);
            return this;
        }

        public WebServerContext webSocket(String path, WebSocketMessageHandler wsHandler) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add WebSocket handlers after the server has started.");
            }
            wsEndPoints.put(Pattern.compile("^" + path + "$"), wsHandler);
            return this;
        }

        public WebServerContext filter(HttpMethods method, String path, Filter filter) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add filters after the server has started.");
            }

            FilterEntry o = new FilterEntry(Pattern.compile("^" + path + "$"), filter);
            List<FilterEntry> filterEntries = filters.get(method);
            for (FilterEntry filterEntry : filterEntries) {
                if (o.pattern.pattern().equals(filterEntry.pattern.pattern())) {
                    throw new IllegalStateException("Filter already registered for " + path);
                }
            }
            filterEntries.add(o);
            return this;
        }

        public WebServerContext filter(String path, Filter filter) {
            return filter(HttpMethods.ALL, path, filter);
        }

        public WebServerContext serveStaticFilesAsync(String basePath, String directory) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add static serving after the server has started.");
            }
            endPoint(HttpMethods.GET, basePath + "/(.*)", (req, res, ctx) -> {
                String filePath = ctx.getParam("1");
                Path path = Paths.get(directory, filePath);
                if (Files.exists(path) && !Files.isDirectory(path)) {
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return Files.readAllBytes(path);
                        } catch (IOException e) {
                            throw new ServerException("Internal Static File Serving error for " + path, e);
                        }
                    }).thenAccept(fileBytes -> {
                        String contentType;
                        try {
                            contentType = Files.probeContentType(path);
                            if (contentType == null) {
                                contentType = "application/octet-stream";
                            }
                        } catch (IOException e) {
                            contentType = "application/octet-stream";
                        }
                        res.setHeader("Content-Type", contentType);
                        res.write(new String(fileBytes, StandardCharsets.UTF_8), 200);
                    }).exceptionally(ex -> {
                        sendErrorResponse(res.exchange, 500, "Internal server error");
                        return null;
                    });
                } else {
                    sendErrorResponse(res.exchange, 404, "Not found");
                }
            });
            return this;
        }
    }

    public static class PathContext extends AbstractWebServerContext {

        public PathContext(ServerState serverState) {
            super(serverState);
        }

    }

    public static class Config {
        public final InetSocketAddress inetSocketAddress;
        public final int wsPort;
        public final int wsBacklog;
        public final InetAddress wsBindAddr;
        public final int socketTimeoutMs;

        private final int connectionTimeoutMs;
        private final int serverOperationTimeoutMs;

        private Config(InetSocketAddress inetSocketAddress, int wsPort, int wsBacklog, InetAddress wsBindAddr, int socketTimeoutMs, int connectionTimeoutMs, int serverOperationTimeoutMs) {
            this.inetSocketAddress = inetSocketAddress;
            this.wsPort = wsPort;
            this.wsBacklog = wsBacklog;
            this.wsBindAddr = wsBindAddr;
            this.socketTimeoutMs = socketTimeoutMs;
        }

        public static Config create() {
            return new Config(null, 0, 50, null, 30000, 10000, 60000);
        }

        public Config withInetSocketAddress(InetSocketAddress inetSocketAddress) {
            return new Config(inetSocketAddress, this.wsPort, this.wsBacklog, this.wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withWebSocketPort(int wsPort) {
            return new Config(this.inetSocketAddress, wsPort, this.wsBacklog, this.wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withWsBacklog(int wsBacklog) {
            return new Config(this.inetSocketAddress, this.wsPort, wsBacklog, this.wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withHostAndWebPort(String host, int webPort) {
            return new Config(new InetSocketAddress(host, webPort), this.wsPort, this.wsBacklog, this.wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withWsBindAddr(InetAddress wsBindAddr) {
            return new Config(this.inetSocketAddress, this.wsPort, this.wsBacklog, wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withSocketTimeoutMillis(int socketTimeoutMs) {
            return new Config(this.inetSocketAddress, this.wsPort, this.wsBacklog, this.wsBindAddr, socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withWebPort(int webPort) {
            return new Config(new InetSocketAddress(webPort), this.wsPort, this.wsBacklog, this.wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withConnectionTimeoutMillis(int connectionTimeoutMs) {
            return new Config(this.inetSocketAddress, this.wsPort, this.wsBacklog, this.wsBindAddr, this.socketTimeoutMs, connectionTimeoutMs, this.serverOperationTimeoutMs);
        }

        public Config withServerOperationTimeoutMillis(int serverOperationTimeoutMs) {
            return new Config(this.inetSocketAddress, this.wsPort, this.wsBacklog, this.wsBindAddr, this.socketTimeoutMs, this.connectionTimeoutMs, serverOperationTimeoutMs);

        }
    }

    public static class WebServer extends AbstractWebServerContext {

        private final HttpServer httpServer;
        private final WebSocketServer socketServer;
        private Thread simpleWebSocketServerThread = null;
        private Config config;
        private final DependencyManager dependencyManager;

        public WebServer(Config config) {
            this(config, new DependencyManager(new DefaultComponentCache(null)));
        }

        public WebServer(Config config, DependencyManager dependencyManager) {
            super(new ServerState());
            this.config = config;
            this.dependencyManager = dependencyManager;
            try {
                httpServer = makeHttpServer();
            } catch (IOException | ArrayIndexOutOfBoundsException e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace(System.err);
                throw new ServerException("Could not create HttpServer", e);
            }

            for (HttpMethods method : HttpMethods.values()) {
                endPoints.put(method, new HashMap<>());
                filters.put(method, new ArrayList<>());
            }

            if (config.wsPort > 0) {
                socketServer = new WebSocketServer(config, dependencyManager) {
                    @Override
                    protected WebSocketMessageHandler getHandler(String path) {
                        for (Map.Entry<Pattern, WebSocketMessageHandler> patternWebSocketMessageHandlerEntry : wsEndPoints.entrySet()) {
                            Pattern key = patternWebSocketMessageHandlerEntry.getKey();
                            WebSocketMessageHandler value = patternWebSocketMessageHandlerEntry.getValue();
                            if (key.matcher(path).matches()) {
                                return value;
                            }
                        }
                        return FOUR_OH_FOUR;
                    }
                };
            } else {
                socketServer = null;
            }

            httpServer.createContext("/", exchange -> {
                handleHttpRequest(dependencyManager, exchange);
            });
        }

        private void handleHttpRequest(DependencyManager dependencyManager, HttpExchange exchange) {
            String path = exchange.getRequestURI().getPath();
            HttpMethods method = HttpMethods.valueOf(exchange.getRequestMethod());

            Map<Pattern, EndPoint> methodEndPoints = endPoints.get(method);
            if (methodEndPoints == null) {
                sendErrorResponse(exchange, 405, "Method not allowed");
                return;
            }

            List<FilterStat> filterSequence = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            boolean routeMatched = false;
            Map<String, Object> stats = new HashMap<>();
            stats.put("filters", filterSequence);

            try {

                for (Map.Entry<Pattern, EndPoint> route : methodEndPoints.entrySet()) {
                    Matcher matcher = route.getKey().matcher(path);
                    if (matcher.matches()) {
                        routeMatched = true;
                        Map<String, String> params = new HashMap<>();
                        int groupCount = matcher.groupCount();
                        Pattern key = route.getKey();
                        for (int i = 1; i <= groupCount; i++) {
                            params.put(String.valueOf(i), matcher.group(i));
                        }

                        final Request request = new Request(exchange);
                        final Response response = new Response(exchange);
                        final Map<String, Object> attributes = new HashMap<>();
                        final ComponentCache requestCache = new DefaultComponentCache(dependencyManager.cache);

                        // Apply filters
                        List<FilterEntry> methodFilters = filters.get(method);
                        if (methodFilters == null) {
                            methodFilters = new ArrayList<FilterEntry>();
                        }
                        methodFilters.addAll(filters.get(HttpMethods.ALL));

                        for (FilterEntry filterEntry : methodFilters) {
                            Matcher filterMatcher = filterEntry.pattern.matcher(path);
                            if (filterMatcher.matches()) {
                                if (handleFilterMatch(exchange, filterEntry, filterMatcher, request, response, attributes, requestCache, matcher, filterSequence) == FilterAction.STOP) {
                                    // stop chain of execution
                                    return;
                                }
                            }
                        }

                        handleEndPointMatch(exchange, route, request, response, params, attributes, requestCache, matcher, stats);
                        // matched route
                        return;
                    }
                }

                // route unmatched
                if (!routeMatched) {
                    sendErrorResponse(exchange, 404, "Not found");
                    stats.put("endpoint", "unmatched");
                    stats.put("status", 404);
                }

            } finally {
                stats.put("path", path);
                stats.put("duration", System.currentTimeMillis() - startTime);
                recordStatistics(path, stats);
            }
        }

        private void handleEndPointMatch(HttpExchange exchange, Map.Entry<Pattern, EndPoint> route, Request request, Response response, Map<String, String> params, Map<String, Object> attributes, ComponentCache requestCache, Matcher matcher, Map<String, Object> stats) {
            long endPointStartTime = System.currentTimeMillis();
            try {
                try {
                    route.getValue().handle(request, response, createRequestContext(params, attributes, requestCache, matcher));

                    stats.put("endpoint", route.getKey().pattern());
                    stats.put("status", response.exchange.getResponseCode());

                } catch (Throwable e) {
                    stats.put("endpoint", route.getKey().pattern() + " -Exception");
                    stats.put("status", 500);
                    exceptionDuringHandling(e, exchange);
                    return;
                }
            } catch (ServerException e) {
                stats.put("endpoint", route.getKey().pattern() + " -ServerException");
                stats.put("status", 500);

                serverException(e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
                return;
            } finally {
                stats.put("endpointDuration", System.currentTimeMillis() - endPointStartTime);
            }
        }

        private FilterAction handleFilterMatch(HttpExchange exchange, FilterEntry filterEntry, Matcher filterMatcher, Request request, Response response, Map<String, Object> attributes, ComponentCache requestCache, Matcher matcher, List<FilterStat> filterSequence) {
            Map<String, String> filterParams = new HashMap<>();
            for (int i = 1; i <= filterMatcher.groupCount(); i++) {
                filterParams.put(String.valueOf(i), filterMatcher.group(i));
            }
            long filterStartTime = System.currentTimeMillis();
            try {
                FilterAction result;
                try {
                    result = filterEntry.filter.filter(request, response, createRequestContext(filterParams, attributes, requestCache, matcher));
                    filterSequence.add(new FilterStat(filterEntry.pattern.pattern(), "ok", System.currentTimeMillis() - filterStartTime));
                    return result;
                } catch (Exception e) {
                    filterSequence.add(new FilterStat(filterEntry.pattern.pattern(), "exception", System.currentTimeMillis() - filterStartTime));
                    exceptionDuringHandling(e, exchange);
                    return FilterAction.STOP;
                }
            } catch (ServerException e) {
                filterSequence.add(new FilterStat(filterEntry.pattern.pattern(), "server-exception", System.currentTimeMillis() - filterStartTime));
                serverException(e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
                return FilterAction.STOP;
            }
         }

        protected HttpServer makeHttpServer() throws IOException {

            HttpServer s = HttpServer.create();
            s.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // Use serverOperationTimeoutMs if needed
            return s;

            // How to participate in Idle Timeouts - override this method
            //ServerSocket socket = server.getAddress().getSocket();
            //socket.setSoTimeout(timeoutInMillis); // Set the idle timeout in milliseconds

            // Socket timeouts is harder, google for: "HttpServer in the com.sun.net.httpserver package lacks direct
            //    support for setting a custom ServerSocket with SO_TIMEOUT but how do I make it work with subclasses?"

        }

        private RequestContext createRequestContext(Map<String, String> params, Map<String, Object> attributes, ComponentCache requestCache, Matcher matcher) {
            return new ServerRequestContext(params, dependencyManager, requestCache, matcher, attributes);
        }

        protected void recordStatistics(String path, Map<String, Object> stats) {
            // This method is intentionally left empty for now.
        }

        protected void serverException(ServerException e) {
            System.err.println(e.getMessage() + "\nStack Trace:");
            e.printStackTrace(System.err);
        }

        /**
         * Most likely a RuntimeException or Error in a endPoint() or filter() code block
         */
        protected void exceptionDuringHandling(Throwable e, HttpExchange exchange) {
            sendErrorResponse(exchange, 500, "Server error");
            //System.err.println(e.getMessage() + "\nStack Trace:");
            //e.printStackTrace(System.err);
        }

        public WebServer start() {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Server has already been started.");
            }
            try {
                httpServer.bind(config.inetSocketAddress, 0);
            } catch (IOException e) {
                throw new ServerException("Can't listen on port " + config.inetSocketAddress.getPort(), e);
            }


            httpServer.start();
            serverState.start();
            if (socketServer != null) {
                simpleWebSocketServerThread = new Thread(socketServer::start);
                simpleWebSocketServerThread.start();
                //simpleWebSocketServerThread.setDaemon(true);
            }
            return this;
        }

        public WebServer stop() {
            httpServer.stop(0);
            if (simpleWebSocketServerThread != null) {
                socketServer.stop();
                simpleWebSocketServerThread.interrupt();
            }
            return this;
        }

        private static class ServerRequestContext implements RequestContext {

            private final Map<String, String> params;
            private final DependencyManager dependencyManager;
            private final ComponentCache requestCache;
            private final Matcher matcher;
            private final Map<String, Object> attributes;

            public ServerRequestContext(Map<String, String> params, DependencyManager dependencyManager, ComponentCache requestCache, Matcher matcher, Map<String, Object> attributes) {
                this.params = params;
                this.dependencyManager = dependencyManager;
                this.requestCache = requestCache;
                this.matcher = matcher;
                this.attributes = attributes;
            }

            @Override
            public String getParam(String key) {
                return params.get(key);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T dep(Class<T> clazz) {
                return dependencyManager.instantiateDep(clazz, requestCache, matcher);
            }

            public void setAttribute(String key, Object value) {
                attributes.put(key, value);
            }

            public Object getAttribute(String key) {
                return attributes.get(key);
            }

            public Matcher getMatcher() {
                return matcher;
            }
        }
    }

    public static class ServerComposition implements WebServerContext {
        private final WebServer server;

        public ServerComposition(WebServer server) {
            this.server = server;
        }

        @Override
        public PathContext path(String basePath, Runnable runnable) {
            return server.path(basePath, runnable);
        }

        @Override
        public void sendErrorResponse(HttpExchange exchange, int code, String message) {
            server.sendErrorResponse(exchange, code, message);
        }

        @Override
        public WebServerContext endPoint(HttpMethods method, String path, EndPoint endPoint) {
            return server.endPoint(method, path, endPoint);
        }

        @Override
        public WebServerContext webSocket(String path, WebSocketMessageHandler wsHandler) {
            return server.webSocket(path, wsHandler);
        }

        @Override
        public WebServerContext filter(HttpMethods method, String path, Filter filter) {
            return server.filter(method, path, filter);
        }

        @Override
        public WebServerContext filter(String path, Filter filter) {
            return server.filter(path, filter);
        }

        @Override
        public WebServerContext serveStaticFilesAsync(String basePath, String directory) {
            return server.serveStaticFilesAsync(basePath, directory);
        }
    }


    /* ==========================
     * Supporting Classes
     * ==========================
     */
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

        private Map<String, String> queryParams;

        public Request(HttpExchange exchange) {
            this.exchange = exchange;
            if (exchange != null) {
                try {
                    this.body = new String(exchange.getRequestBody().readAllBytes());

                } catch (IOException e) {
                    throw new ServerException("Internal request error, for " + exchange.getRequestURI(), e);
                }
            } else {
                this.body = null;
            }
        }
        public String getCookie(String name) {
            List<String> cookiesHeader = exchange.getRequestHeaders().get("Cookie");
            if (cookiesHeader != null) {
                for (String cookie : cookiesHeader) {
                    String[] cookies = cookie.split(";");
                    for (String c : cookies) {
                        String[] keyValue = c.trim().split("=");
                        if (keyValue.length > 1 && keyValue[0].equals(name)) {
                            return c.trim().substring(keyValue[0].length()+1);
                        }
                    }
                }
            }
            return null;
        }

        public String getBody() { return body; }
        public Map<String, List<String>> getHeaders() { return exchange.getRequestHeaders(); }
        public String getPath() { return exchange.getRequestURI().getPath(); }
        public String getQuery() {
            if (exchange != null) {
                return exchange.getRequestURI().getQuery();
            }
            return "";
        }


        // TODO: NEEDED
        public Map<String, String> getQueryParams() {
            if (queryParams != null) {
                return queryParams;
            }
            String query = getQuery();
            if (query == null || query.isEmpty()) {
                return Collections.emptyMap();
            }
            this.queryParams = new HashMap<>();
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

        public void sendResponse(String content, int statusCode) {
            sendResponse(content.getBytes(StandardCharsets.UTF_8), statusCode, false);
        }

        public void sendResponseChunked(String content, int statusCode) {
            sendResponse(content.getBytes(StandardCharsets.UTF_8), statusCode, true);
        }

        private void sendResponse(byte[] content, int statusCode, boolean chunked) {
            try {
                if (chunked) {
                    exchange.getResponseHeaders().set("Transfer-Encoding", "chunked");
                    exchange.sendResponseHeaders(statusCode, 0);
                    OutputStream out = exchange.getResponseBody();
                    writeChunk(out, content);
                    writeChunk(out, new byte[0]); // End of chunks
                    out.close();
                } else {
                    exchange.sendResponseHeaders(statusCode, content.length);
                    exchange.getResponseBody().write(content);
                    exchange.getResponseBody().close();
                }
            } catch (IOException e) {
                throw new ServerException("Internal response error, for " + exchange.getRequestURI(), e);
            }
        }

        public void writeChunk(OutputStream out, byte[] chunk) throws IOException {
            String chunkSize = Integer.toHexString(chunk.length) + "\r\n";
            out.write(chunkSize.getBytes(StandardCharsets.US_ASCII));
            out.write(chunk);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        public OutputStream getResponseBody() {
            return this.exchange.getResponseBody();
        }

        public void sendResponseHeaders(int i, int i1) throws IOException {
            exchange.sendResponseHeaders(i, i1);
        }
    }

    public interface ComponentCache {
        <T> T getOrCreate(Class<T> clazz, Supplier<T> supplier);

        ComponentCache getParent();

        <T> void put(Class<T> clazz, T instance);
    }

    // I'm not sure about this class - Paul
    public static class UseOnceComponentCache implements ComponentCache {
        public static final String SEE_TINY_WEB_S_DEPENDENCY_TESTS = "See TinyWeb's DependencyTests";
        private ComponentCache hidden;

        public UseOnceComponentCache(ComponentCache hidden) {
            this.hidden = hidden;
        }

        @Override
        public <T> T getOrCreate(Class<T> clazz, Supplier<T> supplier) {
            throw new AssertionError(SEE_TINY_WEB_S_DEPENDENCY_TESTS);
        }

        @Override
        public ComponentCache getParent() {
            throw new AssertionError(SEE_TINY_WEB_S_DEPENDENCY_TESTS);
        }

        @Override
        public <T> void put(Class<T> clazz, T instance) {
            throw new AssertionError(SEE_TINY_WEB_S_DEPENDENCY_TESTS);
        }
        public ComponentCache getHidden() {
            if (hidden != null) {
                ComponentCache toReturn = hidden;
                hidden = null;
                return toReturn;
            } else {
                throw new AssertionError("See TinyWeb's DependencyTests");
            }
        }
    }

    public static class DefaultComponentCache implements ComponentCache {
        private final Map<Class<?>, Object> cache = new ConcurrentHashMap<>();
        private final ComponentCache parentCache;

        public DefaultComponentCache() {
            this(null);
        }

        public DefaultComponentCache(ComponentCache parentCache) {
            this.parentCache = parentCache;
        }

        @Override
        public ComponentCache getParent() {
            return parentCache;
        }

        @Override
        public <T> void put(Class<T> clazz, T instance) {
            cache.put(clazz, instance);
        }

        @Override
        public <T> T getOrCreate(Class<T> clazz, Supplier<T> supplier) {
            return (T) cache.computeIfAbsent(clazz, key -> supplier.get());
        }
    }

    public static class ServerException extends RuntimeException {
        public ServerException(String message, Throwable cause) {
            super(message, cause);
        }

        public ServerException(String message) {
            super(message);
        }
    }

    public record FilterStat (String path, String result, long duration) {}


    /* ==========================
     * WebSocket Classes
     * ==========================
     */


    public static class WebSocketServer {

        public static final Pattern ORIGIN_MATCH = Pattern.compile("Origin: (.*)");
        public static final WebSocketMessageHandler BAD_ORIGIN = (message, sender, ctx) -> sender.sendBytesFrame(toBytes("Error: Bad Origin"));
        public static final WebSocketMessageHandler FOUR_OH_FOUR = (message, sender, ctx) -> sender.sendBytesFrame(toBytes("Error: 404"));
        private final Config config;
        private ServerSocket server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private static final SecureRandom random = new SecureRandom();
        private Map<String, WebSocketMessageHandler> messageHandlers = new HashMap<>();
        private final DependencyManager dependencyManager;

        public WebSocketServer(Config config) {
            this(config, new DependencyManager(new DefaultComponentCache(null)));
        }
        public WebSocketServer(Config config, DependencyManager dependencyManager) {
            this.config = config;
            this.dependencyManager = dependencyManager;

        }
        public void registerMessageHandler(String path, WebSocketMessageHandler handler) {
            this.messageHandlers.put(path, handler);
        }

        public void start() {
            try {
                server = new ServerSocket(config.wsPort, config.wsBacklog, config.wsBindAddr);

                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        client.setSoTimeout(config.getConnectionTimeoutMs());
                        client.setKeepAlive(true);
                        clientConnected(client);

                        executor.execute(() -> handleClient(client));
                    } catch (SocketException e) {
                        if (e.getMessage().equals("Socket closed")) {
                            // likely just the server being shut down programmatically.
                        } else {
                            throw e;
                        }
                    }
                }
            } catch (IOException e) {
                throw new ServerException("Can't start WebSocket Server", e);
            }
        }

        protected void clientConnected(Socket client) {
        }

        private void handleClient(Socket client) {
            Scanner s = null;
            try {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                s = new Scanner(in, "UTF-8");

                String data = s.useDelimiter("\\r\\n\\r\\n").next();
                Matcher get = Pattern.compile("^GET").matcher(data);

                if (get.find()) {
                    Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                    if (!match.find()) {
                        client.close();
                        return;
                    }

                    // Capture the Origin header
                    Matcher originMatch = ORIGIN_MATCH.matcher(data);
                    String origin = originMatch.find() ? originMatch.group(1) : "Unknown";

                    // Handle handshake
                    String acceptKey = generateAcceptKey(match.group(1));
                    sendHandshakeResponse(out, acceptKey);

                    // Handle WebSocket communication
                    handleWebSocketCommunication(client, in, out, origin);
                }
            } catch (SocketTimeoutException e) {
                // TODO handle read timed out
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                s.close();
                try {
                    client.close();
                } catch (IOException e) {
                }
            }

        }

        private String generateAcceptKey(String webSocketKey) throws UnsupportedEncodingException {
            try {
                return Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1")
                                .digest((webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                        .getBytes("UTF-8")));
            } catch (NoSuchAlgorithmException e) {
                throw new ServerException("NoSuchAlgorithm", e);
            }
        }

        private void sendHandshakeResponse(OutputStream out, String acceptKey) throws IOException {
            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n").getBytes("UTF-8");

            out.write(response);
            out.flush();
        }

        private void handleWebSocketCommunication(Socket client, InputStream in, OutputStream out, String origin) throws IOException {
            //System.out.println("origin= " + origin);
            String expectedOrigin = config.inetSocketAddress != null ? (config.inetSocketAddress.getHostName() + ":" + config.inetSocketAddress.getPort()).replace("0.0.0.0", "").replace("::", "") : "";

            com.paulhammant.tiny.Tiny.MessageSender sender = new com.paulhammant.tiny.Tiny.MessageSender(out);
            byte[] buffer = new byte[8192];

            while (!client.isClosed()) {
                // Read frame header
                int headerLength = readFully(in, buffer, 0, 2);
                if (headerLength < 2) break;

                boolean fin = (buffer[0] & 0x80) != 0;
                int opcode = buffer[0] & 0x0F;
                boolean masked = (buffer[1] & 0x80) != 0;
                int payloadLength = buffer[1] & 0x7F;
                int offset = 2;
                
                // Handle extended payload length cases
                if (payloadLength == 126) {
                    headerLength = readFully(in, buffer, offset, 2);
                    if (headerLength < 2) break;
                    payloadLength = ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
                    offset += 2;
                } else if (payloadLength == 127) {
                    headerLength = readFully(in, buffer, offset, 8);
                    if (headerLength < 8) break;
                    payloadLength = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLength |= (buffer[offset + i] & 0xFF) << ((7 - i) * 8);
                    }
                    offset += 8;
                }
                
                // Handle close frame immediately
                if (opcode == 8) { // Close frame
                    sendCloseFrame(out);
                    break;
                }

                // Only process payload for data frames (text or binary)
                if (opcode == 1 || opcode == 2) {
                    // Read masking key
                    byte[] maskingKey = null;
                    if (masked) {
                        headerLength = readFully(in, buffer, offset, 4);
                        if (headerLength < 4) break;
                        maskingKey = Arrays.copyOfRange(buffer, offset, offset + 4);
                        offset += 4;
                    }

                    // Skip processing if there's no payload
                    if (payloadLength == 0) continue;

                    // Read and unmask the entire payload at once
                    byte[] payload = new byte[payloadLength];
                    int bytesRead = readFully(in, payload, 0, payloadLength);
                    if (bytesRead < payloadLength) break;
                    
                    // Unmask the entire payload
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] = (byte) (payload[i] ^ maskingKey[i & 0x3]);
                        }
                    }

                    // Now work with the unmasked payload
                    if (payload.length > 0) {
                        int pathLength = payload[0] & 0xFF;

                        if (pathLength > payload.length - 1) {
                            invalidPathLength(pathLength, payload);
                            break;
                        }

                        // Extract path
                        byte[] pathBytes = Arrays.copyOfRange(payload, 1, pathLength + 1);
                        String path = new String(pathBytes, StandardCharsets.US_ASCII);

                        // Extract message
                        byte[] messagePayload = Arrays.copyOfRange(payload, pathLength + 1, payload.length);

                        CompletableFuture.runAsync(() -> {

                            if (!origin.endsWith(expectedOrigin)) {
                                BAD_ORIGIN.handleMessage(null, sender, null);
                            } else{
                                ComponentCache requestCache = new DefaultComponentCache(dependencyManager.cache);
                                RequestContext ctx = new WebServer.ServerRequestContext(new HashMap<>(), dependencyManager, requestCache, null, new HashMap<>());
                                getHandler(path).handleMessage(messagePayload, sender, ctx); // could be 404 handler

                            }
                        });
                    }
                }
                // Other control frames (ping/pong) could be handled here
            }
        }

        protected static void invalidPathLength(int pathLength, byte[] payload) {
            System.err.println("Invalid path length: " + pathLength + " (payload length: " + payload.length + ")");
        }

        protected WebSocketMessageHandler getHandler(String path) {
            return messageHandlers.get(path);
        }

        private void sendCloseFrame(OutputStream out) throws IOException {
            out.write(new byte[]{(byte) 0x88, 0x00}); // Close frame with empty payload
            out.flush();
        }

        private static int readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
            int totalBytesRead = 0;
            while (totalBytesRead < length) {
                int bytesRead = in.read(buffer, offset + totalBytesRead, length - totalBytesRead);
                if (bytesRead == -1) break;
                totalBytesRead += bytesRead;
            }
            return totalBytesRead;
        }

        public void stop() {
            try {
                if (server != null && !server.isClosed()) {
                    server.close();
                }
            } catch (IOException e) {
                throw new ServerException("Can't stop WebSocket Server", e);
            }
        }
    }

    public static class WebSocketClient implements AutoCloseable {
        private final Socket socket;
        private final String host;
        private final int port;
        private final String originUrl;
        private Consumer<String> onMessageHandler;
        private final InputStream in;
        private final OutputStream out;
        private static final SecureRandom random = new SecureRandom();

        public WebSocketClient(String host, int port, String originUrl) throws IOException {
            this.host = host;
            this.port = port;
            this.originUrl = originUrl;
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(300000); // 5 minutes timeout
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        public void performHandshake() throws IOException {
            byte[] keyBytes = new byte[16];
            random.nextBytes(keyBytes);
            String key = Base64.getEncoder().encodeToString(keyBytes);
            String handshakeRequest =
                    "GET / HTTP/1.1\r\n" +
                            "Host: " + this.host +":" +this.port+ "\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Key: " + key + "\r\n" +
                            "Sec-WebSocket-Version: 13\r\n" +
                            "Origin: " + this.originUrl + "\r\n\r\n";

            out.write(handshakeRequest.getBytes("UTF-8"));
            out.flush();

            // Read handshake response
            byte[] responseBuffer = new byte[1024];
            int responseBytes = in.read(responseBuffer);
            String response = new String(responseBuffer, 0, responseBytes, "UTF-8");
            if (!response.contains("HTTP/1.1 101 Switching Protocols") ||
                !response.contains("Connection: Upgrade") ||
                !response.contains("Upgrade: websocket")) {
                throw new IOException("websocket handshake failed, unexpected response: " + response);
            }
        }

        public void sendMessage(String path, String message) throws IOException {
            byte[] pathBytes = path.getBytes(StandardCharsets.US_ASCII);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] mask = new byte[4];
            random.nextBytes(mask);

            // Calculate total payload length (1 byte for path length + path + message)
            int totalLength = 1 + pathBytes.length + messageBytes.length;

            // Write frame header
            out.write(0x81); // FIN bit set, text frame
            out.write(0x80 | totalLength); // Mask bit set, payload length

            // Write mask
            out.write(mask);

            // Create complete payload first
            byte[] fullPayload = new byte[totalLength];
            fullPayload[0] = (byte) pathBytes.length;
            System.arraycopy(pathBytes, 0, fullPayload, 1, pathBytes.length);
            System.arraycopy(messageBytes, 0, fullPayload, 1 + pathBytes.length, messageBytes.length);

            // Mask the entire payload
            for (int i = 0; i < fullPayload.length; i++) {
                out.write(fullPayload[i] ^ mask[i & 0x3]);
            }

            out.flush();
        }

        //  returns true if stop required, otherwise clients should reconnect
        public boolean receiveMessages(String stopPhrase, InterruptibleConsumer<String> handle) throws IOException {
            boolean keepGoing = true;
            while (keepGoing) {
                // Read frame header
                int byte1 = in.read();
                if (byte1 == -1) break;

                int byte2 = in.read();
                if (byte2 == -1) break;

                int payloadLength = byte2 & 0x7F;

                // Handle extended payload length
                if (payloadLength == 126) {
                    byte[] extendedLength = new byte[2];
                    WebSocketServer.readFully(in, extendedLength, 0, 2);
                    payloadLength = ((extendedLength[0] & 0xFF) << 8) | (extendedLength[1] & 0xFF);
                } else if (payloadLength == 127) {
                    byte[] extendedLength = new byte[8];
                    WebSocketServer.readFully(in, extendedLength, 0, 8);
                    payloadLength = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLength |= (extendedLength[i] & 0xFF) << ((7 - i) * 8);
                    }
                }

                byte[] payload = new byte[payloadLength];
                int bytesRead = WebSocketServer.readFully(in, payload, 0, payloadLength);
                if (bytesRead < payloadLength) break;


                String message = new String(payload, 0, bytesRead, "UTF-8");
                if (message.equals(stopPhrase)) {
                    return true;
                } else {
                    keepGoing = handle.accept(message);
                }
            }
            return false;
        }

        private void sendClose() throws IOException {
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            out.write(new byte[]{(byte) 0x88, (byte) 0x80, mask[0], mask[1], mask[2], mask[3]});
            out.flush();
        }

        @Override
        public void close() throws IOException {
            sendClose();
            socket.close();
        }
    }

    public static class MessageSender {
        private final OutputStream outputStream;

        public MessageSender(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void sendBytesFrame(byte[] payload) {
            try {
                outputStream.write(0x81); // FIN bit set, text frame
                if (payload.length < 126) {
                    outputStream.write(payload.length);
                } else if (payload.length <= 65535) {
                    outputStream.write(126);
                    outputStream.write((payload.length >> 8) & 0xFF);
                    outputStream.write(payload.length & 0xFF);
                } else {
                    outputStream.write(127);
                    for (int i = 7; i >= 0; i--) {
                        outputStream.write((payload.length >> (8 * i)) & 0xFF);
                    }
                }
                outputStream.write(payload);
                outputStream.flush();
            } catch (IOException e) {
                throw new com.paulhammant.tiny.Tiny.ServerException("IOE " + e.getMessage(), e);
            }
        }
    }

    /* ==========================
     * Miscellaneous
     * ==========================
     */
    public static class JavascriptWebSocketClient implements EndPoint {
        @Override
        public void handle(Request request, Response response, RequestContext context) {
            response.setHeader("Content-Type", "javascript");
            response.sendResponse("""
                    // Define TinyWeb in the global scope
                    window.TinyWeb = {
                        SocketClient: class SocketClient {
                            socket;
                            messageCallback;
                            stopPhrase;
                    
                            constructor(host, port) {
                                this.socket = new WebSocket(`ws://${host}:${port}`);
                                this.socket.binaryType = 'arraybuffer';
                               
                                this.socket.onopen = () => {
                                    console.log("WebSocket connection opened");
                                };
                    
                                this.socket.onmessage = (event) => {
                                    if (this.messageCallback) {
                                        try {
                                            let data;
                                            if (event.data instanceof ArrayBuffer) {
                                                data = new Uint8Array(event.data);
                                            } else if (typeof event.data === 'string') {
                                                data = new TextEncoder().encode(event.data);
                                            } else {
                                                console.error('Unsupported data type received');
                                                return;
                                            }
                                           
                                            const message = new TextDecoder('utf-8').decode(data);
                                            if (message === this.stopPhrase) {
                                                this.socket.close();
                                            } else {
                                                this.messageCallback(message);
                                            }
                                        } catch (error) {
                                            console.error('Error processing message:', error);
                                        }
                                    }
                                };
                    
                                this.socket.onerror = (error) => {
                                    console.error('WebSocket error:', error);
                                };
                            }
                    
                            async sendMessage(path, message) {
                                await this.waitForOpen();
                               
                                return new Promise((resolve, reject) => {
                                    try {
                                        const pathBytes = new TextEncoder().encode(path);
                                        const messageBytes = new TextEncoder().encode(message);
                                        const totalLength = 1 + pathBytes.length + messageBytes.length;
                                        const payload = new Uint8Array(totalLength);
                                       
                                        payload[0] = pathBytes.length;
                                        payload.set(pathBytes, 1);
                                        payload.set(messageBytes, 1 + pathBytes.length);
                                       
                                        this.socket.send(payload);
                                        resolve();
                                    } catch (error) {
                                        reject(error);
                                    }
                                });
                            }
                    
                            receiveMessages(stopPhrase, callback) {
                                this.stopPhrase = stopPhrase;
                                this.messageCallback = callback;
                            }
                    
                            async close() {
                                return new Promise((resolve) => {
                                    if (this.socket.readyState === WebSocket.OPEN) {
                                        this.socket.close(1000);
                                    }
                                    resolve();
                                });
                            }
                    
                            async waitForOpen() {
                                if (this.socket.readyState === WebSocket.OPEN) {
                                    return Promise.resolve();
                                }
                    
                                return new Promise((resolve, reject) => {
                                    const checkOpen = () => {
                                        if (this.socket.readyState === WebSocket.OPEN) {
                                            resolve();
                                        } else if (this.socket.readyState === WebSocket.CLOSED ||
                                                 this.socket.readyState === WebSocket.CLOSING) {
                                            reject(new Error('WebSocket closed before connection could be established'));
                                        } else {
                                            setTimeout(checkOpen, 100);
                                        }
                                    };
                                    checkOpen();
                                });
                            }
                        }
                    };
                """, 200);
        }
    }

    public static class DependencyException extends RuntimeException {
        public final Class clazz;

        public <T> DependencyException(Class clazz) {
            this("Wrong scope or not a component at all " + clazz.getName(), clazz);
        }

        public <T> DependencyException(String reason, Class clazz) {
            super(reason);
            this.clazz = clazz;
        }
    }

    public static byte [] toBytes(String responseMessage) {
        try {
            return responseMessage.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("never happens");
        }
    }

}
