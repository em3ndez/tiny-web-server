package com.paulhammant.tinywebserver;

import jakarta.websocket.server.ServerEndpointConfig;

public class WebSocketConfigurator extends ServerEndpointConfig.Configurator {
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (endpointClass.equals(WebSocketHandler.class)) {
            return endpointClass.cast(new WebSocketHandler());
        }
        throw new InstantiationException("Unable to create endpoint instance");
    }
}
