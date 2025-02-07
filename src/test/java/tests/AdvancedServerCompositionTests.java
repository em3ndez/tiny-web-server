package tests;

import com.paulhammant.tiny.Tiny;
import org.forgerock.cuppa.Test;
import org.jetbrains.annotations.NotNull;

import static com.paulhammant.tiny.Tiny.FilterAction.CONTINUE;
import static com.paulhammant.tiny.Tiny.HttpMethods.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class AdvancedServerCompositionTests {
    Tiny.WebServer webServer;

    {
        describe("When additional composition can happen on a previously instantiated Tiny.WebServer", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                    endPoint(GET, "/foo", (req, res, ctx) -> {
                        res.write("Hello1");
                    });
                }};
                new Tiny.ServerComposition(webServer) {{
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
        describe("When additional additional composition can happen", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                    endPoint(GET, "/foo", (req, res, ctx) -> {
                        res.write("Hello1");
                    });
                }};
                new Tiny.ServerComposition(webServer) {{
                    path("/advertising", () -> {
                        path("/selling", () -> {
                            endPoint(GET, ".*", (req, res, ctx) -> {
                                res.write("Hello");
                            });
                        });
                    });
                }};
            });
            it("The it can't get around prior path reservation the inline way", () -> {
                try {
                    new Tiny.ServerComposition(webServer) {{
                        path("/advertising", () -> {
                            path("/buying", () -> {
                                endPoint(GET, ".*", (req, res, ctx) -> {
                                    res.write("Hello");
                                });
                            });
                        });
                    }};
                    webServer.start();
                    throw new AssertionError("should never get here");
                } catch (IllegalStateException e) {
                    assertThat(e.getMessage(), equalTo("Path already registered: /advertising"));
                }

            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
        describe("When additional additional composition can happen with a composition that specifies a path", () -> {
            beforeEach(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                    endPoint(GET, "/foo", (req, res, ctx) -> {
                        res.write("Hello1");
                    });
                }};
                new Tiny.ServerComposition(webServer, "/advertising") {{
                    path(rootPath, () -> {
                        path("/selling", () -> {
                            endPoint(GET, ".*", (req, res, ctx) -> {
                                res.write("Hello");
                            });
                        });
                    });
                }};
            });
            it("Then it can't get around prior path reservation", () -> {
                try {
                    new Tiny.ServerComposition(webServer) {{
                        path("/advertising", () -> {
                            path("/buying", () -> {
                                endPoint(GET, ".*", (req, res, ctx) -> {
                                    res.write("Hello");
                                });
                            });
                        });

                    }};
                    webServer.start();
                    throw new AssertionError("should never get here");
                } catch (IllegalStateException e) {
                    assertThat(e.getMessage(), equalTo("Path already registered: /advertising"));
                }

            });
            it("Then it can't get around prior path reservation via composition class that specifies a rootPath", () -> {
                try {
                    new Tiny.ServerComposition(webServer, "/advertising") {{
                        path(rootPath, () -> {
                            // If you are using rootPath in the constructor, you MUST have a path(rootPath, () - {...});
                            path("/buying", () -> {
                                endPoint(GET, ".*", (req, res, ctx) -> {
                                    res.write("Hello");
                                });
                            });
                        });
                    }};
                    webServer.start();
                    throw new AssertionError("should never get here");
                } catch (IllegalStateException e) {
                    assertThat(e.getMessage(), equalTo("Path already registered: /advertising"));
                }

            });
            it("Then it can't get around prior path reservation via concrete composition class", () -> {
                try {
                    new AdBuyingServerComposition(webServer,"/advertising");
                    webServer.start();
                    throw new AssertionError("should never get here");
                } catch (IllegalStateException e) {
                    assertThat(e.getMessage(), equalTo("Path already registered: /advertising"));
                }

            });
            it("Then it can have a concrete composition class with a different rootPath", () -> {
                new AdBuyingServerComposition(webServer,"/ads"); // within there's a /buying path
                webServer.start();
                try (okhttp3.Response response = httpGet("/ads/buying")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().string(),
                            equalTo("Hello from AdBuyingServerComposition"));
                }
            });
            afterEach(() -> {
                webServer.stop();
                webServer = null;
            });
        });
        describe("Given a Tiny web server with ConcreteExtensionToServerComposition", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
                    path("/a", () -> {
                        path("/b", () -> {
                            path("/c", () -> {
                                new tests.ConcreteExtensionToServerComposition(this);
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
        describe("Given a started Tiny web server", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{

                }};
                webServer.start();
            });
            describe("When additional composition happens", () -> {
                it("Then illegal state errors should happen for new paths()", () -> {
                    try {
                        new Tiny.ServerComposition(webServer) {{
                            path("/a", () -> {});
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add paths after the server has started."));
                    }
                });
                it("Then illegal state errors should happen for new 'all' filters()", () -> {
                    try {
                        new Tiny.ServerComposition(webServer) {{
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
                        new Tiny.ServerComposition(webServer) {{
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
                        new Tiny.ServerComposition(webServer) {{
                            endPoint(GET, "/a", (req, resp, ctx) -> {});
                        }};
                        throw new AssertionError("should have barfed");
                    } catch (IllegalStateException e) {
                        assertThat(e.getMessage(), equalTo("Cannot add endpoints after the server has started."));
                    }
                });
                it("Then illegal state errors should happen for new endPoints()", () -> {
                    try {
                        new Tiny.ServerComposition(webServer) {{
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

    private static class AdBuyingServerComposition extends Tiny.ServerComposition {
        public AdBuyingServerComposition(Tiny.WebServer webServer, String rootPath) {
            super(webServer, rootPath); {{
                path(rootPath, () -> {
                    // If you are using rootPath in the constructor, you MUST have a path(rootPath, () - {...});
                    path("/buying", () -> {
                        endPoint(GET, ".*", (req, res, ctx) -> {
                            res.write("Hello from AdBuyingServerComposition");
                        });
                    });
                });
            }};
        }
    }
}
