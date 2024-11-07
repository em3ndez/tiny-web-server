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
        //ONE_MORE_TEST_HERE
        describe("Given a TinyWeb server with ConcreteExtensionToServerComposition", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/a", () -> {
                        path("/b", () -> {
                            path("/c", () -> {
                                new ConcreteExtensionToServerComposition(this);
                            });
                        });
                    });
                }};
                webServer.start();
            });
            describe("When that concrete class is mounted within another path", () -> {
                it("Then endPoints should be able to work relatively", () -> {
                    try (okhttp3.Response response = httpGet("/a/b/c/bar/baz")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(),
                                equalTo("Hello from (relative) /bar/baz (absolute path: /a/b/c/bar/baz)"));
                    }
                });
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
