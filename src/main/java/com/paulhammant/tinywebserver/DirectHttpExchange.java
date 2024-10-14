package com.paulhammant.tinywebserver;

import com.sun.net.httpserver.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DirectHttpExchange extends HttpExchange {

    private final URI requestURI;
    private final String requestMethod;
    private final Headers requestHeaders;
    private final ByteArrayOutputStream responseBody;
    private final Headers responseHeaders;
    private int responseCode;

    public DirectHttpExchange(String method, URI uri, Map<String, List<String>> headers) {
        this.requestMethod = method;
        this.requestURI = uri;
        this.requestHeaders = new Headers();
        this.requestHeaders.putAll(headers);
        this.responseBody = new ByteArrayOutputStream();
        this.responseHeaders = new Headers();
    }

    @Override
    public Headers getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        return requestURI;
    }

    @Override
    public String getRequestMethod() {
        return requestMethod;
    }

    @Override
    public HttpContext getHttpContext() {
        return null; // Not applicable for direct mode
    }

    @Override
    public void close() {
        // No resources to close in direct mode
    }

    @Override
    public InputStream getRequestBody() {
        return new ByteArrayInputStream(new byte[0]); // Empty request body
    }

    @Override
    public OutputStream getResponseBody() {
        return responseBody;
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) {
        this.responseCode = rCode;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("localhost", 0); // Dummy address
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("localhost", 0); // Dummy address
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public Object getAttribute(String name) {
        return null; // No attributes in direct mode
    }

    @Override
    public void setAttribute(String name, Object value) {
        // No attributes in direct mode
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
        // Not used in direct mode
    }

    @Override
    public HttpPrincipal getPrincipal() {
        //TODO implement correctly
    }

    @Override
    public Request with(String headerName, List<String> headerValues) {
        //TODO implement correctly
    }
}
