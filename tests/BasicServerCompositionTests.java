package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;

import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class BasicServerCompositionTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with composed paths", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(TinyWeb.Config.create().withHostAndWebPort("localhost", 8080)) {{
                    path("/composed", () -> {
                        path("/nested", () -> {
                            endPoint(GET, "/endpoint", (req, res, ctx) -> {
                                res.write("Composed path response");
                            });
                        });
                    });
                    path("/alpha", () -> {
                        path("/beta", () -> {
                            endPoint(GET, "/gamma", (req, res, ctx) -> {
                                res.write("Second Composed path response");
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
                assertThat(httpGet("/composed/nested/").code(), equalTo(404));
                assertThat(httpGet("/composed/").code(), equalTo(404));

                try (okhttp3.Response response = httpGet("/alpha/beta/gamma")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().string(), equalTo("Second Composed path response"));
                }
                assertThat(httpGet("/alpha/beta/").code(), equalTo(404));
                assertThat(httpGet("/alpha").code(), equalTo(404));
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
