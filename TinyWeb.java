/*
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

package com.paulhammant.tnywb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TinyWeb {

    public static final String VERSION = "1.0.0";

    /* ==========================
     * Enums
     * ==========================
     */
    public enum Method { ALL, GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, CONNECT, TRACE, LINK, UNLINK, LOCK,
        UNLOCK, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, REPORT, SEARCH, PURGE, REBIND, UNBIND, ACL}

    public enum FilterResult {
        CONTINUE, STOP
    }

    /* ==========================
     * Core Classes
     * ==========================
     */
    public static class ServerState {
        private boolean hasStarted;
        public boolean hasStarted() {
            return hasStarted;
        }

        public void start() {
            hasStarted = true;
        }
    }

    public static class DependencyManager {
        protected final ComponentCache cache;

        public DependencyManager(ComponentCache cache) {
            this.cache = cache;
        }

        public <T> T instantiateDep(Class<T> clazz, ComponentCache requestCache) {
            // Implement logic to instantiate or retrieve the dependency
            // For example, using the cache to manage instances
            return requestCache.getOrCreate(clazz, () -> {
                // Add instantiation logic here
                throw new IllegalArgumentException("Unsupported class: " + clazz);
            });
        }
    }

    public static class ServerContext implements IServerContext {

        Map<Method, Map<Pattern, EndPoint>> routes = new HashMap<>();
        Map<Pattern, SocketMessageHandler> wsRoutes = new HashMap<>();
        Map<Method, List<FilterEntry>> filters = new HashMap<>() {{ put(Method.ALL, new ArrayList<>()); }};
        protected final ServerState serverState;

        public ServerContext(ServerState serverState) {
            this.serverState = serverState;
        }

        public PathContext path(String basePath, Runnable routes) {
            // Save current routes and filters
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot modify routes after the server has started.");
            }
            Map<Method, Map<Pattern, EndPoint>> previousRoutes = this.routes;
            Map<Pattern, SocketMessageHandler> previousWsRoutes = this.wsRoutes;
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
            for (Map.Entry<Pattern, SocketMessageHandler> entry : this.wsRoutes.entrySet()) {
                Pattern pattern = entry.getKey();
                SocketMessageHandler wsHandler = entry.getValue();
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

            return new PathContext(basePath, this, serverState);
        }

        // TODO was protected
        public void sendErrorResponse(HttpExchange exchange, int code, String message) {
                new Response(exchange).write(message, code);
        }

        public IServerContext endPoint(TinyWeb.Method method, String path, EndPoint endPoint) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add endpoints after the server has started.");
            }
            routes.computeIfAbsent(method, k -> new HashMap<>())
                    .put(Pattern.compile("^" + path + "$"), endPoint);
            return this;
        }

        public IServerContext webSocket(String path, SocketMessageHandler wsHandler) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add WebSocket handlers after the server has started.");
            }
            wsRoutes.put(Pattern.compile("^" + path + "$"), wsHandler);
            return this;
        }


        public IServerContext filter(TinyWeb.Method method, String path, Filter filter) {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Cannot add filters after the server has started.");
            }
            filters.computeIfAbsent(method, k -> new ArrayList<>())
                    .add(new FilterEntry(Pattern.compile("^" + path + "$"), filter));
            return this;
        }

        public IServerContext filter(String path, Filter filter) {
            return filter(Method.ALL, path, filter);
        }

        public IServerContext serveStaticFiles(String basePath, String directory) {
            endPoint(Method.GET, basePath + "/(.*)", (req, res, ctx) -> {
                String filePath = ctx.getParam("1");
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
                    sendErrorResponse(res.exchange, 404, "Not found");
                }
            });
            return this;
        }
    }

    public static class PathContext extends ServerContext {

        private final String basePath;
        private final ServerContext parentContext;

        public PathContext(String basePath, ServerContext parentContext, ServerState serverState) {
            super(serverState);
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

    public static class Server extends ServerContext {

        private final HttpServer httpServer;
        private final SocketServer socketServer;
        private Thread simpleWebSocketServerThread = null;
        private final DependencyManager dependencyManager;

        public Server(int httpPort, int webSocketPort) {
            this(httpPort, webSocketPort, new DependencyManager(new DefaultComponentCache(null)));
        }

        public Server(int httpPort, int webSocketPort, DependencyManager dependencyManager) {
            super(new ServerState());
            this.dependencyManager = dependencyManager;
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
                        System.out.println("No websocket handler for " + path);
                        return (message, sender) -> sender.sendTextFrame("no matching path on the server side".getBytes("UTF-8"));
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
                    sendErrorResponse(exchange, 405, "Method not allowed");
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
                            params.put(String.valueOf(i), matcher.group(i));
                        }

                        final Request request = new Request(exchange, this);
                        final Response response = new Response(exchange);
                        final Map<String, Object> attributes  = new HashMap<>();
                        final ComponentCache requestCache = new DefaultComponentCache(dependencyManager.cache);

                        // Apply filters
                        List<FilterEntry> methodFilters = filters.get(method);
                        if (methodFilters == null) {
                            methodFilters = new ArrayList<FilterEntry>();
                        }
                        methodFilters.addAll(filters.get(Method.ALL));

                        for (FilterEntry filterEntry : methodFilters) {
                            Matcher filterMatcher = filterEntry.pattern.matcher(path);
                            if (filterMatcher.matches()) {
                                Map<String, String> filterParams = new HashMap<>();
                                for (int i = 1; i <= filterMatcher.groupCount(); i++) {
                                    filterParams.put(String.valueOf(i), filterMatcher.group(i));
                                }
                                try {
                                    FilterResult result;
                                    try {
                                        result = filterEntry.filter.filter(request, response, createRequestContext(filterParams, attributes, requestCache));
                                    } catch (Exception e) {
                                        exceptionDuringHandling(e);
                                        sendErrorResponse(exchange, 500, "Server Error");
                                        return;
                                    }
                                    if (result == FilterResult.STOP) {
                                        return; // Stop processing if filter returns STOP
                                    }
                                } catch (ServerException e) {
                                    serverException(e);
                                    sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
                                    return;
                                }
                            }
                        }

                        try {

                            try {
                                route.getValue().handle(request, response, createRequestContext(params, attributes, requestCache));
                            } catch (Exception e) {
                                exceptionDuringHandling(e);
                                sendErrorResponse(exchange, 500, "Server error");
                                return;
                            }
                        } catch (ServerException e) {
                            serverException(e);
                            sendErrorResponse(exchange, 500,"Internal server error: " + e.getMessage());
                            return;
                        }
                        return;
                    }
                }

                if (!routeMatched) {
                    sendErrorResponse(exchange, 404, "Not found");
                }
            });
        }

        private RequestContext createRequestContext(Map<String, String> params, Map<String, Object> attributes, ComponentCache requestCache) {
            return new RequestContext() {

                @Override
                public String getParam(String key) {
                    return params.get(key);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> T dep(Class<T> clazz) {
                    return dependencyManager.instantiateDep(clazz, requestCache);
                }

                public void setAttribute(String key, Object value) {
                    attributes.put(key, value);
                }

                public Object getAttribute(String key) {
                    return attributes.get(key);
                }

            };
        }

        protected void serverException(ServerException e) {
            System.err.println(e.getMessage() + "\nStack Trace:");
            e.printStackTrace(System.err);
        }

        /**
         * Most likely a RuntimeException or Error in a endPoint() or filter() code block
         */
        protected void exceptionDuringHandling(Exception e) {
            System.err.println(e.getMessage() + "\nStack Trace:");
            e.printStackTrace(System.err);
        }

        public TinyWeb.Server start() {
            if (serverState.hasStarted()) {
                throw new IllegalStateException("Server has already been started.");
            }
            httpServer.start();
            serverState.start();
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

    public interface IServerContext {
        PathContext path(String basePath, Runnable routes);

        void sendErrorResponse(HttpExchange exchange, int code, String message);

        IServerContext endPoint(Method method, String path, EndPoint endPoint);

        IServerContext webSocket(String path, SocketMessageHandler wsHandler);

        IServerContext filter(Method method, String path, Filter filter);

        IServerContext filter(String path, Filter filter);

        IServerContext serveStaticFiles(String basePath, String directory);
    }

    public static class ServerComposition implements IServerContext {
        private final Server server;

        public ServerComposition(Server server) {
            this.server = server;
        }

        @Override
        public PathContext path(String basePath, Runnable routes) {
            return server.path(basePath, routes);
        }

        @Override
        public void sendErrorResponse(HttpExchange exchange, int code, String message) {
            server.sendErrorResponse(exchange, code, message);
        }

        @Override
        public IServerContext endPoint(Method method, String path, EndPoint endPoint) {
            return server.endPoint(method, path, endPoint);
        }

        @Override
        public IServerContext webSocket(String path, SocketMessageHandler wsHandler) {
            return server.webSocket(path, wsHandler);
        }

        @Override
        public IServerContext filter(Method method, String path, Filter filter) {
            return server.filter(method, path, filter);
        }

        @Override
        public IServerContext filter(String path, Filter filter) {
            return server.filter(path, filter);
        }

        @Override
        public IServerContext serveStaticFiles(String basePath, String directory) {
            return server.serveStaticFiles(basePath, directory);
        }
    }

    /* ==========================
     * Interfaces
     * ==========================
     */
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
        FilterResult filter(Request request, Response response, RequestContext ctx);
    }

    @FunctionalInterface
    public interface SocketMessageHandler {
        void handleMessage(byte[] message, TinyWeb.MessageSender sender) throws IOException;
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
        private final Server server;
        private final String body;

        // TODO why unused?
        private Map<String, String> queryParams;

        public Request(HttpExchange exchange, Server server) {
            this.exchange = exchange;
            this.server = server;
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

    public interface ComponentCache {
        <T> T getOrCreate(Class<T> clazz, Supplier<T> supplier);

        ComponentCache getParent();

        <T> void put(Class<T> clazz, T instance);
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
            System.out.println("Cache: " + clazz.getName() + " being sought");
            return (T) cache.computeIfAbsent(clazz, key -> supplier.get());
        }
        // direct put of instance here
    }

    public static class ServerException extends RuntimeException {
        public ServerException(String message, Throwable cause) {
            super(message, cause);
        }

        public ServerException(String message) {
            super(message);
        }
    }

    /* ==========================
     * WebSocket Classes
     * ==========================
     */
    public static class SocketServer {

        private final int port;
        private ServerSocket server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private static final int SOCKET_TIMEOUT = 30000; // 30 seconds timeout
        private static final SecureRandom random = new SecureRandom();
        private Map<String, SocketMessageHandler> messageHandlers = new HashMap<>();

        public SocketServer(int port) {
            this.port = port;
        }

        public void registerMessageHandler(String path, SocketMessageHandler handler) {
            this.messageHandlers.put(path, handler);
        }

        public void start() {
            try {
                server = new ServerSocket(port);

                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        client.setSoTimeout(SOCKET_TIMEOUT);
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
            try {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                Scanner s = new Scanner(in, "UTF-8");

                try {
                    String data = s.useDelimiter("\\r\\n\\r\\n").next();
                    Matcher get = Pattern.compile("^GET").matcher(data);

                    if (get.find()) {
                        Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                        if (!match.find()) {
                            client.close();
                            return;
                        }

                        // Handle handshake
                        String acceptKey = generateAcceptKey(match.group(1));
                        sendHandshakeResponse(out, acceptKey);

                        // Handle WebSocket communication
                        handleWebSocketCommunication(client, in, out);
                    }
                } finally {
                    s.close();
                    client.close();
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
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

        private void handleWebSocketCommunication(Socket client, InputStream in, OutputStream out) throws IOException {
            TinyWeb.MessageSender sender = new TinyWeb.MessageSender(out);
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

                        SocketMessageHandler handler = getHandler(path);
                        if (handler != null) {
                            handler.handleMessage(messagePayload, sender);
                        } else {
                            System.err.println("No handler found for path: " + path + " keys:" + messageHandlers.keySet());
                        }
                    }
                }
                // Other control frames (ping/pong) could be handled here
            }
        }

        protected static void invalidPathLength(int pathLength, byte[] payload) {
            System.err.println("Invalid path length: " + pathLength + " (payload length: " + payload.length + ")");
        }

        protected SocketMessageHandler getHandler(String path) {
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

    public static class SocketClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private static final SecureRandom random = new SecureRandom();

        public SocketClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(5000);
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        public void performHandshake() throws IOException {
            String key = "dGhlIHNhbXBsZSBub25jZQ=="; // In practice, generate this randomly
            String handshakeRequest =
                    "GET / HTTP/1.1\r\n" +
                            "Host: localhost:8081\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Key: " + key + "\r\n" +
                            "Sec-WebSocket-Version: 13\r\n\r\n";

            out.write(handshakeRequest.getBytes("UTF-8"));
            out.flush();

            // Read handshake response
            byte[] responseBuffer = new byte[1024];
            int responseBytes = in.read(responseBuffer);
            String response = new String(responseBuffer, 0, responseBytes, "UTF-8");
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

        public String receiveMessage() throws IOException {
            // Read frame header
            int byte1 = in.read();
            if (byte1 == -1) return null;

            int byte2 = in.read();
            if (byte2 == -1) return null;

            int payloadLength = byte2 & 0x7F;

            // Handle extended payload length
            if (payloadLength == 126) {
                byte[] extendedLength = new byte[2];
                SocketServer.readFully(in, extendedLength, 0, 2);
                payloadLength = ((extendedLength[0] & 0xFF) << 8) | (extendedLength[1] & 0xFF);
            } else if (payloadLength == 127) {
                byte[] extendedLength = new byte[8];
                SocketServer.readFully(in, extendedLength, 0, 8);
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength |= (extendedLength[i] & 0xFF) << ((7 - i) * 8);
                }
            }

            byte[] payload = new byte[payloadLength];
            int bytesRead = SocketServer.readFully(in, payload, 0, payloadLength);
            if (bytesRead < payloadLength) return null;

            return new String(payload, 0, bytesRead, "UTF-8");
        }

        public void sendClose() throws IOException {
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

        public void sendTextFrame(byte[] payload) throws IOException {
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
        }
    }

    /* ==========================
     * Miscellaneous
     * ==========================
     */
    public static class JavascriptSocketClient implements EndPoint {
        @Override
        public void handle(Request request, Response response, RequestContext context) {
            response.setHeader("Content-Type", "javascript");
            response.sendResponse("""
                const TinyWeb = {
                    SocketClient: class SocketClient {
                        socket;
            
                        constructor(host, port) {
                            this.socket = new WebSocket(`ws://${host}:${port}`);
                            this.socket.binaryType = 'arraybuffer';
                        }
            
                        async sendMessage(path, message) {
                            return new Promise((resolve, reject) => {
                                if (this.socket.readyState !== WebSocket.OPEN) {
                                    reject(new Error('WebSocket is not open'));
                                    return;
                                }
            
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
            
                        async receiveMessage() {
                            return new Promise((resolve, reject) => {
                                const handleMessage = (event) => {
                                    // Remove the listener to prevent multiple triggers
                                    this.socket.removeEventListener('message', handleMessage);
            
                                    try {
                                        let data;
                                        if (event.data instanceof ArrayBuffer) {
                                            data = new Uint8Array(event.data);
                                        } else if (typeof event.data === 'string') {
                                            // If the server sends data as text, convert it to Uint8Array
                                            data = new TextEncoder().encode(event.data);
                                        } else {
                                            reject(new Error('Unsupported data type received'));
                                            return;
                                        }
                                        const message = new TextDecoder('utf-8').decode(data);
                                        resolve(message);
                                    } catch (error) {
                                        reject(error);
                                    }
                                };
            
                                this.socket.addEventListener('message', handleMessage);
                                this.socket.addEventListener('error', (error) => {
                                    reject(error);
                                });
                            });
                        }
            
                        async close() {
                            return new Promise((resolve) => {
                                if (this.socket.readyState === WebSocket.OPEN) {
                                    this.socket.close(1000); // Normal closure
                                }
                                resolve();
                            });
                        }
            
                        async waitForOpen() {
                            if (this.socket.readyState === WebSocket.OPEN) {
                                return Promise.resolve();
                            }
            
                            return new Promise((resolve, reject) => {
                                this.socket.addEventListener('open', () => resolve());
                                this.socket.addEventListener('error', (error) => reject(error));
                            });
                        }
                    }
                };                                        
                """, 200);
        }
    }
}
