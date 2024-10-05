import java.io.IOException;
import java.util.function.Consumer;

public class WebServerBuilder {
    private final WebServer server;

    public WebServerBuilder(int port) throws IOException {
        server = new WebServer(port);
    }

    public WebServerBuilder addHandler(WebServer.Method method, String path, WebServer.Handler handler) {
        server.addHandler(method, path, handler);
        return this;
    }

    public WebServerBuilder path(String prefix, Consumer<WebServerBuilder> routeDefinitions) {
        WebServer.RouteGroup group = server.path(prefix);
        routeDefinitions.accept(this);
        group.end();
        return this;
    }

    public WebServer build() {
        return server;
    }
}
