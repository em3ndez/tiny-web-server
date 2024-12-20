package tests;

import com.sun.net.httpserver.HttpExchange;
import org.forgerock.cuppa.Test;

import com.paulhammant.tiny.Tiny;
import static com.paulhammant.tiny.Tiny.HttpMethods.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class WebServerTests {
    Tiny.WebServer webServer;
    ExampleApp exampleApp;

    {

        describe("Given an inlined Cuppa application", () -> {
            describe("When the endpoint can extract parameters", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                        path("/api", () -> {
                            path("/v1", () -> {
                                endPoint(GET, "/test/(\\w+)", (req, res, ctx) -> {
                                    res.write("Parameter: " + ctx.getParam("1"));
                                });
                            });
                        });
                    }}.start();
                });
                it("Then it should extract parameters correctly from the path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/test/123"), "Parameter: 123", 200);
                });
                it("Then it should return 404 when two parameters are provided for a one-parameter path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/test/123/456"), "Not found", 404);
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When the endpoint can extract query parameters", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        path("/api2", () -> {
                            endPoint(GET, "/test/(\\w+)", (req, res, ctx) -> {
                                res.write("Parameter: " + ctx.getParam("1") + " " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });
                it("Then it should handle query parameters correctly", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api2/test/123?a=1&b=2"), "Parameter: 123 {a=1, b=2}", 200);
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });;
            describe("When an application exception is thrown from an endpoint", () -> {
                final StringBuilder appHandlingExceptions = new StringBuilder();
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                            path("/api", () -> {
                                endPoint(GET, "/error", (req, res, ctx) -> {
                                    throw new RuntimeException("Deliberate exception");
                                });
                            });
                        }
                        @Override
                        protected void exceptionDuringHandling(Throwable e, HttpExchange exchange) {
                            appHandlingExceptions.append("appHandlingException exception: " + e.getMessage());
                            super.exceptionDuringHandling(e, exchange);
                        }
                    }.start();
                });

                it("Then it should return 500 and an error message for a runtime exception", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/error"),
                        "Server error", 500);
                    assertThat(appHandlingExceptions.toString(),
                            equalTo("appHandlingException exception: Deliberate exception"));
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When the endpoint has query-string parameters", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        path("/api", () -> {
                            endPoint(GET, "/query", (req, res, ctx) -> {
                                res.write("Query Params: " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });

                it("Then it should parse query parameters correctly", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/query?name=John&age=30"),
                            "Query Params: {name=John, age=30}", 200);
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When response headers are sent to the client", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
                        path("/api", () -> {
                            endPoint(GET, "/header-test", (req, res, ctx) -> {
                                res.setHeader("X-Custom-Header", "HeaderValue");
                                res.write("Header set");
                            });
                        });
                    }}.start();
                });

                it("Then it should set the custom header correctly", () -> {
                    try (okhttp3.Response response = httpGet("/api/header-test")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.header("X-Custom-Header"), equalTo("HeaderValue"));
                        assertThat(response.body().string(), equalTo("Header set"));
                    }
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When an exception is thrown from a filter", () -> {
                final StringBuilder appHandlingExceptions = new StringBuilder();
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        path("/api", () -> {
                            filter(GET, "/error", (req, res, ctx) -> {
                                throw new RuntimeException("Deliberate exception in filter");
                            });
                            endPoint(GET, "/error", (req, res, ctx) -> {
                                res.write("This should not be reached");

                            });
                        });
                    }

                        @Override
                        protected void exceptionDuringHandling(Throwable e, HttpExchange exchange) {
                            appHandlingExceptions.append("appHandlingException exception: " + e.getMessage());
                            super.exceptionDuringHandling(e, exchange);
                        }
                    }.start();
                });

                it("Then it should return 500 and an error message for a runtime exception in a filter", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/error"),
                                "Server error", 500);
                    assertThat(appHandlingExceptions.toString(),
                            equalTo("appHandlingException exception: Deliberate exception in filter"));
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When testing static file serving", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        serveStaticFilesAsync("/static", ".");
                    }}.start();
                });

                it("Then it should serve a static file correctly", () -> {
                    try (okhttp3.Response response = httpGet("/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("Cuppa-Framework"));
                    }
                });

                it("Then it should return 404 for a non-existent static file", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/static/nonexistent.txt"),
                            "Not found", 404);
                });

                it("Then it should prevent directory traversal attacks", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/static/../../anything.java"),
                            "Not found", 404); //TODO 404?
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When accessing a nested path with parameters", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        final StringBuilder sb = new StringBuilder();  // don't do this - one sv instance for all incoming connections
                        path("/api", () -> {
                            sb.append("/api->"); // called once only while composing webapp
                            path("/v1", () -> {
                                sb.append("/v1->"); // called once only while composing webapp
                                endPoint(GET, "/items/(\\w+)/details/(\\w+)", (req, res, ctx) -> {
                                    sb.append("itemz."); // called while handling request
                                    res.write("Item: " + ctx.getParam("1") + ", Detail: " + ctx.getParam("2") + "\n" + sb);
                                });
                            });
                        });
                    }}.start();
                });

                it("Then it should extract parameters correctly from the nested path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/items/123/details/456"),
                            "Item: 123, Detail: 456\n" +
                                    "/api->/v1->itemz.", 200);
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/items/abc/details/def"),
                            "Item: abc, Detail: def\n" +
                                    "/api->/v1->itemz.itemz.", 200);
                });

                it("Then it should return 404 for an incorrect nested path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/items/123/456"),
                            "Not found", 404);
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When accessing the Echoing GET endpoint", () -> {
                before(() -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        endPoint(GET, "/users/(\\w+)", (req, res, ctx) -> {
                            res.write("User profile: " + ctx.getParam("1"));
                        });
                    }};
                    webServer.start();
                });
                it("Then it should return the user profile for Jimmy", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/users/Jimmy"),
                            "User profile: Jimmy", 200);

                });
                it("Then it should return the user profile for Thelma", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/users/Thelma"),
                            "User profile: Thelma", 200);
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When a server is started", () -> {
                it("Then a endpoint can't be added anymore", () -> {
                    webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                        start();
                        try {
                            endPoint(GET, "/foo", (req, res, ctx) -> {
                            });
                            throw new AssertionError("should have barfed");
                        } catch (IllegalStateException e) {
                            assertThat(e.getMessage(), equalTo("Cannot add endpoints after the server has started."));
                        }
                        stop();
                    }};
                });
                after(() -> {
                    webServer = null;
                });
            });
        });
    }

}
