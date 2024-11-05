import com.paulhammant.tnywb.TinyWeb;

public class ConcreteExtensionToServerComposition extends TinyWeb.ServerComposition {

    public ConcreteExtensionToServerComposition(TinyWeb.Server server) {
        super(server);
        path("/bar", () -> {
            endPoint(TinyWeb.Method.GET, "/baz", (req, res, ctx) -> {
                res.write("Hello from (relative) /bar/baz (absolute path: " +req.getPath() + ")");
            });
        });
    }
}
