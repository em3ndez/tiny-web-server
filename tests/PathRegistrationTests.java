package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;

import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class PathRegistrationTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/duplicate", () -> {
                        endPoint(GET, "/endpoint", (req, res, ctx) -> {
                            res.write("First registration");
                        });
                    });
                    path("/duplicate", () -> {
                        endPoint(GET, "/endpoint", (req, res, ctx) -> {
                            res.write("Second registration");
                        });
                    });
                }};
                webServer.start();
            });

            it("Then it should respond with the second registration message", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/duplicate/endpoint"), "Second registration", 200);
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
