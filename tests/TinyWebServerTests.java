package tests;

import com.paulhammant.tnywb.TinyWeb;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.forgerock.cuppa.Test;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.FilterResult.STOP;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@Test
public class TinyWebServerTests {
    TinyWeb.Server webServer;
    TinyWebTests.ExampleApp exampleApp;

    {
        describe("Given a running TinyWeb server", () -> {
            describe("When accessing the Echoing GET endpoint", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
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

            describe("When accessing a nested path with parameters", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
            describe("When applying filters", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
                        });
                        endPoint(GET, "/baz/baz/baz", (req, res, ctx) -> {
                            res.write("Hello, World 2!");
                        });
                    }};
                    webServer.start();
                });
                it("Then it should allow access when the 'sucks' header is absent", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/foo/bar"),
                            "Hello, World!", 200);

                });
                it("Then it should deny access when the 'sucks' header is present", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/foo/bar", "sucks", "true"),
                            "Access Denied", 403);
                });
                it("Then it can access items outside the path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/baz/baz/baz"),
                            "Hello, World 2!", 200);

                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When serving static files", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        serveStaticFilesAsync("/static", new File(".").getAbsolutePath());
                    }};
                    webServer.start();
                });
                it("Then it should return 200 and serve a text file", () -> {
                    try (okhttp3.Response response = httpGet("/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("text/markdown"));
                        assertThat(response.body().string(), containsString("Directory where compiled classes are stored"));
                    }
                });
                it("Then it should return 404 for non-existent files", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/static/nonexistent.txt"),
                            "Not found", 404);

                });
                it("Then it should return 200 and serve a file from a subdirectory", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (okhttp3.Response response = httpGet("/static/TinyWeb.java")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("text/x-java"));
                        assertThat(response.body().string(), containsString("class"));
                    }
                });
                it("Then it should return 200 and serve a non-text file", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (okhttp3.Response response = httpGet("/static/target/classes/com/paulhammant/tnywb/TinyWeb$Server.class")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("application/java-vm"));
                        assertThat(response.body().string(), containsString("(Lcom/sun/net/httpserver/HttpExchange;ILjava/lang/String;)V"));
                    }
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
        });

        describe("Given a mocked ExampleApp", () -> {
            describe("When accessing the Greeting GET endpoint", () -> {
                before(() -> {
                    exampleApp = Mockito.mock(TinyWebTests.ExampleApp.class);
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        // some of these are not used by the it() tests
                        endPoint(GET, "/greeting/(\\w+)/(\\w+)", exampleApp::foobar);
                    }};
                    //waitForPortToBeClosed("localhost",8080, 8081);
                    webServer.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<TinyWeb.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(exampleApp).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<TinyWeb.RequestContext>any());
                });
                it("Then it should invoke the ExampleApp foobar method", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/greeting/A/B"),
                            "invoked", 200);

                });
                after(() -> {
                    webServer.stop();
                    Mockito.verify(exampleApp).foobar(Mockito.any(TinyWeb.Request.class),
                            Mockito.any(TinyWeb.Response.class),
                            Mockito.<TinyWeb.RequestContext>any());
                    webServer = null;
                });
            });
        });

        describe("Given an inlined Cuppa application", () -> {
            describe("When the endpoint can extract parameters", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
            });
            describe("When endpoint and filters can depend on components", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081, new TinyWeb.DependencyManager(new TinyWeb.DefaultComponentCache(){{
                        this.put(TinyWebTests.ProductInventory.class, new TinyWebTests.ProductInventory(/* would have secrets in real usage */));
                    }}){

                        // This is not Dependency Injection
                        // This also does not use reflection so is fast.

                        @Override
                        public <T> T  instantiateDep(Class<T> clazz, TinyWeb.ComponentCache requestCache) {
                            if (clazz == TinyWebTests.ShoppingCart.class) {
                                return (T) TinyWebTests.createOrGetShoppingCart(requestCache);
                            }
                            throw new IllegalArgumentException("Unsupported class: " + clazz);
                        }

                    });
                    //svr.applicationScopeCache.put()
                    TinyWebTests.doCompositionForOneTest(webServer);
                    webServer.start();

                });
                it("Then it should extract parameters correctly from the path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/howManyOrderInBook"),
                            "Cart Items before: 0\n" +
                            "apple picked: true\n" +
                            "Cart Items after: 1\n", 200);
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When an application exception is thrown from an endpoint", () -> {
                final StringBuilder appHandlingExceptions = new StringBuilder();
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                            path("/api", () -> {
                                endPoint(GET, "/error", (req, res, ctx) -> {
                                    throw new RuntimeException("Deliberate exception");
                                });
                            });
                        }
                        @Override
                        protected void exceptionDuringHandling(Exception e) {
                            appHandlingExceptions.append("appHandlingException exception: " + e.getMessage());
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
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
                    webServer = new TinyWeb.Server(8080, -1) {{
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
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
                        protected void exceptionDuringHandling(Exception e) {
                            appHandlingExceptions.append("appHandlingException exception: " + e.getMessage());
                        }
                    }.start();
                });

                it("Then it should return 500 and an error message for a runtime exception in a filter", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/error"),
                                "Server Error", 500);
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
                    webServer = new TinyWeb.Server(8080, 8081) {{
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
        });
    }

    private static void bodyAndResponseCodeShouldBe(okhttp3.Response response, String bodyShouldBe, int rcShouldBe) throws IOException {
        try (response) {
            assertThat(response.body().string(), equalTo(bodyShouldBe));
            assertThat(response.code(), equalTo(rcShouldBe));
        }
    }

    private static @NotNull okhttp3.Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Request.Builder()
                .url("http://localhost:8080" + url)
                .get().build()).execute();
    }

    private static @NotNull okhttp3.Response httpGet(String url, String hdrKey, String hdrVal) throws IOException {
        return new OkHttpClient().newCall(new Request.Builder()
                .url("http://localhost:8080" + url).addHeader(hdrKey, hdrVal)
                .get().build()).execute();
    }
}
