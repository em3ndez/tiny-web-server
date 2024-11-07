package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

@Test
public class ServerCompositionTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with composed paths", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/composed", () -> {
                        path("/nested", () -> {
                            endPoint(TinyWeb.Method.GET, "/endpoint", (req, res, ctx) -> {
                                res.write("Composed path response");
                            });
                        });
                    });
                }};
                webServer.start();
            });

            it("Then it should respond correctly to requests at the composed endpoint", () -> {
                try (okhttp3.Response response = httpGet("/composed/nested/endpoint")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().string(), equalTo("Composed path response"));
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
