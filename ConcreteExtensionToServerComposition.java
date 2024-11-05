import com.paulhammant.tnywb.TinyWeb;

public class ConcreteExtensionToServerComposition extends TinyWeb.ServerComposition {

    public ConcreteExtensionToServerComposition(TinyWeb.Server server) {
        super(server);
        path("/foo", () -> {
            endPoint(TinyWeb.Method.GET, "/bar", (req, res, ctx) -> {
                res.write("Hello from /foo/bar");
            });
            filter(TinyWeb.Method.GET, "/bar", (req, res, ctx) -> {
                // Example filter logic
                if (req.getHeaders().containsKey("X-Example-Header")) {
                    return TinyWeb.FilterResult.CONTINUE;
                } else {
                    res.write("Forbidden", 403);
                    return TinyWeb.FilterResult.STOP;
                }
            });
        });
    }
}
