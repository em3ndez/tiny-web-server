package tests;

import com.paulhammant.tiny.Tiny;
import org.forgerock.cuppa.Test;

import static com.paulhammant.tiny.Tiny.HttpMethods.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class CompositionReuseTests {
    Tiny.WebServer webServer;

    {
        describe("Given a Tiny web server with a reusable composition", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080)) {{
                    Runnable composition = () -> {
                        endPoint(GET, "/endpoint", (req, res, ctx) -> {
                            res.write("Hello from composed endpoint");
                        });
                    };

                    path("/first", composition);
                    path("/second", composition);
                    path("/third", composition);
                }};
                webServer.start();
            });

            it("Then it should respond correctly to requests at the first composed endpoint", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/first/endpoint"), "Hello from composed endpoint", 200);
            });

            it("Then it should respond correctly to requests at the second composed endpoint", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/second/endpoint"), "Hello from composed endpoint", 200);
            });

            it("Then it should respond correctly to requests at the third composed endpoint", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/third/endpoint"), "Hello from composed endpoint", 200);
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
