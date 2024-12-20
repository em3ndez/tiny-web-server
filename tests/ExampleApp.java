package tests;

import com.paulhammant.tiny.Tiny;
import com.paulhammant.tiny.Tiny.Request;
import com.paulhammant.tiny.Tiny.Response;

import java.io.File;

import static com.paulhammant.tiny.Tiny.FilterAction.CONTINUE;
import static com.paulhammant.tiny.Tiny.FilterAction.STOP;
import static com.paulhammant.tiny.Tiny.HttpMethods.GET;
import static com.paulhammant.tiny.Tiny.HttpMethods.POST;
import static com.paulhammant.tiny.Tiny.HttpMethods.PUT;
import static tests.Suite.bytesToString;
import static tests.Suite.toBytes;

public class ExampleApp {

    public void foobar(Request req, Response res, Tiny.RequestContext ctx) {
        res.write(String.format("Hello, %s %s!", ctx.getParam("1"), ctx.getParam("2")));
    }

    public static Tiny.WebServer exampleComposition(String[] args, ExampleApp app) {
        Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{

            path("/foo", () -> {
                filter(GET, "/.*", (req, res, ctx) -> {
                    if (req.getHeaders().containsKey("sucks")) {
                        res.write("Access Denied", 403);
                        return STOP; // don't proceed
                    }
                    return CONTINUE; // proceed
                });
                endPoint(GET, "/bar", (req, res, ctx) -> {
                    res.write("Hello, World!");
                    // This endpoint is /foo/bar if that wasn't obvious
                });
                webSocket("/eee", (message, sender, context) -> {
                    for (int i = 1; i <= 3; i++) {
                        String responseMessage = "Server sent: " + bytesToString(message) + "-" + i;
                        sender.sendBytesFrame(toBytes(responseMessage));
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                    }
                });
            });

            serveStaticFilesAsync("/static", new File(".").getAbsolutePath());

            endPoint(GET, "/users/(\\w+)", (req, res, ctx) -> {
                res.write("User profile: " + ctx.getParam("1"));
            });

            endPoint(POST, "/echo", (req, res, ctx) -> {
                res.write("You sent: " + req.getBody(), 201);
            });

            endPoint(GET, "/greeting/(\\w+)/(\\w+)", app::foobar);

            endPoint(PUT, "/update", (req, res, ctx) -> {
                res.write("Updated data: " + req.getBody(), 200);
            });

            path("/api", () -> {
                endPoint(Tiny.HttpMethods.GET, "/test/(\\w+)", (req, res, ctx) -> {
                    res.write("Parameter: " + ctx.getParam("1"));
                });
            });
        }};
        return server;
    }

    // You may have a main method here to launch ExampleApp.exampleComposition(..)
}
