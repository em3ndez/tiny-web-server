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
public class ServerCompositionTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with composed paths", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/composed", () -> {
                        path("/nested", () -> {
                            endPoint(GET, "/endpoint", (req, res, ctx) -> {
                                res.write("Composed path response");
                            });
                        });
                    });
                    path("/alpha", () -> {
                        path("/beta", () -> {
                            endPoint(GET, "/gammat", (req, res, ctx) -> {
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
        describe("When additional composition can happen on a previously instantiated TinyWeb.Server", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081) {{
                    endPoint(GET, "/foo", (req, res, ctx) -> {
                        res.write("Hello1");
                    });
                }};
                new TinyWeb.ServerComposition(webServer) {{
                    path("/bar2", () -> {
                        endPoint(GET, "/baz2", (req, res, ctx) -> {
                            res.write("Hello3");
                        });
                    });
                    path("/bar", () -> {
                        endPoint(GET, "/baz", (req, res, ctx) -> {
                            res.write("Hello2");
                        });
                    });
                }};
                new TinyWeb.ServerComposition(webServer) {{
                    endPoint(GET, "/bar2/baz2", (req, res, ctx) -> {
                        res.write("Hello3");
                    });
                }};
                webServer.start();
            });
            it("Then both endpoints should be accessible via GET", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/foo"),
                        "Hello1", 200);
                bodyAndResponseCodeShouldBe(httpGet("/bar/baz"),
                        "Hello2", 200);
                bodyAndResponseCodeShouldBe(httpGet("/bar2/baz2"),
                        "Hello3", 200);
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
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
        describe("Given a started TinyWeb server", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{

                }};
                webServer.start();
            });
            describe("When additional composition happens", () -> {
                it("Then illegal state errors should happen for new paths()", () -> {
                    try {
                        new TinyWeb.ServerComposition(webServer) {{
                            path("/a", () -> {});
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add paths after the server has started."));
                    }
                });
                it("Then illegal state errors should happen for new 'all' filters()", () -> {
                    try {
                        new TinyWeb.ServerComposition(webServer) {{
                            filter("/a", (req, resp, ctx) -> {
                                return CONTINUE;
                            });
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add filters after the server has started."));
                    }
                });
                it("Then illegal state errors should happen for new filters()", () -> {
                    try {
                        new TinyWeb.ServerComposition(webServer) {{
                            filter(GET,"/a", (req, resp, ctx) -> {
                                return CONTINUE;
                            });
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add filters after the server has started."));
                    }
                });
                it("Then illegal state errors should happen for new endPoints()", () -> {
                    try {
                        new TinyWeb.ServerComposition(webServer) {{
                            endPoint(GET, "/a", (req, resp, ctx) -> {});
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add endpoints after the server has started."));
                    }
                });
                it("Then illegal state errors should happen for new endPoints()", () -> {
                    try {
                        new TinyWeb.ServerComposition(webServer) {{
                            serveStaticFilesAsync("foo", "foo");
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add static serving after the server has started."));
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
