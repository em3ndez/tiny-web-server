package com.paulhammant.tinywebserver;

import java.util.*;
import java.util.regex.Pattern;

public class PathContext extends TinyWeb.Context {
    private final String basePath;
    private final TinyWeb.Context parentContext;

    public PathContext(String basePath, TinyWeb.Context parentContext) {
        this.basePath = basePath;
        this.parentContext = parentContext;
    }

    @Override
    public PathContext handle(TinyWeb.Method method, String path, TinyWeb.Handler handler) {
        String fullPath = basePath + path;
        parentContext.routes.computeIfAbsent(method, k -> new HashMap<>())
                .put(Pattern.compile("^" + fullPath + "$"), handler);
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
