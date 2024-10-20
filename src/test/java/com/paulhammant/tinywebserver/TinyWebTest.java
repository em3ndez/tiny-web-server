package com.paulhammant.tinywebserver;

import jakarta.websocket.*;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.mockito.Mockito;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static okhttp3.Request.*;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Test
public class TinyWebTest {
    TinyWeb.ExampleApp app = Mockito.mock(TinyWeb.ExampleApp.class);
    TinyWeb.Server svr;

    {
        describe("ExampleApp.exampleComposition() server tested via sockets", () -> {
            describe("Echoing GET endpoint respond with..", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    svr.start();
                });
                it("should return user profile for Jimmy", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Jimmy")) {
                        assertThat(response.body().string(), equalTo("User profile: Jimmy"));
                    }
                });
                it("should return user profile for Thelma", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Thelma")) {
                        assertThat(response.body().string(), equalTo("User profile: Thelma"));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("Nested path with parameterized parts", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        final StringBuilder sb = new StringBuilder();  // don't do this - one sv instance for all incoming connections
                        path("/api", () -> {
                            sb.append("/api->"); // called once only while composing webapp
                            path("/v1", () -> {
                                sb.append("/v1->"); // called once only while composing webapp
                                endPoint(TinyWeb.Method.GET, "/items/(\\w+)/details/(\\w+)", (req, res, params) -> {
                                    sb.append("itemz."); // called while handling request
                                    res.write("Item: " + params.get("1") + ", Detail: " + params.get("2") + "\n" + sb);
                                });
                            });
                        });
                    }}.start();
                });

                it("should extract parameters correctly from nested path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/v1/items/123/details/456")) {
                        assertThat(response.body().string(), equalTo("Item: 123, Detail: 456\n/api->/v1->itemz."));
                        assertThat(response.code(), equalTo(200));
                    }
                    try (Response response = httpGet("http://localhost:8080/api/v1/items/abc/details/def")) {
                        assertThat(response.body().string(), equalTo("Item: abc, Detail: def\n/api->/v1->itemz.itemz."));
                        assertThat(response.code(), equalTo(200));
                    }
                });

                it("should return 404 for incorrect nested path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/v1/items/123/456")) {
                        assertThat(response.body().string(), equalTo("Not found"));
                        assertThat(response.code(), equalTo(404));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("Filtering", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                });
                it("should allow access when header 'sucks' is absent", () -> {
                    try (Response response = httpGet("http://localhost:8080/foo/bar")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), equalTo("Hello, World!"));
                    }
                });
                it("should deny access when header 'sucks' is present", () -> {
                    try (Response response = httpGet("http://localhost:8080/foo/bar", "sucks", "true")) {
                        assertThat(response.code(), equalTo(403));
                        assertThat(response.body().string(), equalTo("Access Denied"));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("WebSocket functionality", () -> {
                before(() -> {
                    svr = TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    svr.start();
                });

                it("should echo messages sent to the WebSocket", () -> {
                    final CountDownLatch messageLatch = new CountDownLatch(1);
                    final String messageToSend = "Hello WebSocket";
                    final StringBuilder receivedMessage = new StringBuilder();

                    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                    Session session = container.connectToServer(new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            try {
                                session.addMessageHandler((MessageHandler.Whole<String>) message -> {
                                    receivedMessage.append(message);
                                    messageLatch.countDown();
                                });
                                session.getBasicRemote().sendText(messageToSend);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }, URI.create("ws://localhost:8080/websocket"));

                    messageLatch.await(5, TimeUnit.SECONDS);
                    assertThat(messageToSend, equalTo(receivedMessage.toString()));
                    session.close();
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("Static file serving functionality", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    svr.start();
                });
                it("should return 200 and serve a text file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("text/markdown"));
                        assertThat(response.body().string(), containsString("hello"));
                    }
                });
                it("should return 404 for non-existent files", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("Not found"));
                    }
                });
                it("should return 200 and serve a file from a subdirectory", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/src/main/java/com/paulhammant/tinywebserver/TinyWeb.java")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("text/x-java"));
                        assertThat(response.body().string(), containsString("class"));
                    }
                });
                it("should return 200 and serve a non-text file", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/target/classes/com/paulhammant/tinywebserver/TinyWeb$Server.class")) {
                        assertThat(response.code(), equalTo(200));
                        // Expected: "application/java-vm"
                        //     but: was "text/plain; charset=UTF-8" TODO
                        assertThat(response.body().contentType().toString(), equalTo("application/java-vm"));
                        assertThat(response.body().string(), containsString("(Lcom/sun/net/httpserver/HttpExchange;ILjava/lang/String;)V"));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
        });

        describe("ExampleApp.exampleComposition() app tested with Mockito", () -> {
            describe("Greeting GET endpoint", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<TinyWeb.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(app).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<Map<String, String>>any());
                });
                it("should invoke ExampleApp foobar method", () -> {
                    try (Response response = httpGet("http://localhost:8080/greeting/A/B")) {
                        assertThat(response.body().string(), equalTo("invoked"));
                    }
                });
                after(() -> {
                    svr.stop();
                    Mockito.verify(app).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<Map<String, String>>any());
                    svr = null;
                });
            });
        });

        describe("Inline application tests", () -> {
            describe("Can extract parameters", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                            path("/api", () -> {
                                endPoint(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, params) -> {
                                    res.write("Parameter: " + params.get("1"));
                                });
                            });
                        }}.start();
                });
                it("should extract parameters correctly from path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/test/123")) {
                        assertThat(response.body().string(), equalTo("Parameter: 123"));
                        assertThat(response.code(), equalTo(200));
                    }
                });
                it("should return 404 when two params are provided for a one param path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/test/123/456")) {
                        assertThat(response.body().string(), equalTo("Not found"));
                        assertThat(response.code(), equalTo(404));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("Can extract query Parameters", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        path("/api2", () -> {
                            endPoint(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, params) -> {
                                res.write("Parameter: " + params + " " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });
                it("should handle query parameters correctly", () -> {
                    try (Response response = httpGet("http://localhost:8080/api2/test/123?a=1&b=2")) {
                        assertThat(response.body().string(), equalTo("Parameter: {1=123} {a=1, b=2}"));
                        assertThat(response.code(), equalTo(200));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("Exception handling in endpoint", () -> {
                final StringBuilder se = new StringBuilder();
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        path("/api", () -> {
                            endPoint(TinyWeb.Method.GET, "/error", (req, res, params) -> {
                                throw new RuntimeException("Deliberate exception");
                            });
                        });
                    }

                        @Override
                        protected void appHandlingException(Exception e) {
                            se.append("appHandlingException exception: " + e.getMessage());
                        }
                    }.start();
                });

                it("should return 500 and error message for runtime exception", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/error")) {
                        assertThat(response.code(), equalTo(500));
                        assertThat(response.body().string(), containsString("Server error"));
                        assertThat(se.toString(), containsString("appHandlingException exception: Deliberate exception"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("Query parameter parsing", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        path("/api", () -> {
                            endPoint(TinyWeb.Method.GET, "/query", (req, res, params) -> {
                                res.write("Query Params: " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });

                it("should parse query parameters correctly", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/query?name=John&age=30")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), equalTo("Query Params: {name=John, age=30}"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("Response header setting", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        path("/api", () -> {
                            endPoint(TinyWeb.Method.GET, "/header-test", (req, res, params) -> {
                                res.setHeader("X-Custom-Header", "HeaderValue");
                                res.write("Header set");
                            });
                        });
                    }}.start();
                });

                it("should set custom header correctly", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/header-test")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.header("X-Custom-Header"), equalTo("HeaderValue"));
                        assertThat(response.body().string(), equalTo("Header set"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("Exception handling in filter", () -> {
                final StringBuilder se = new StringBuilder();
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        path("/api", () -> {
                            filter(TinyWeb.Method.GET, "/error", (req, res, params) -> {
                                throw new RuntimeException("Deliberate exception in filter");
                            });
                            endPoint(TinyWeb.Method.GET, "/error", (req, res, params) -> {
                                res.write("This should not be reached");
                            });
                        });
                    }

                        @Override
                        protected void appHandlingException(Exception e) {
                            se.append("appHandlingException exception: " + e.getMessage());
                        }
                    }.start();
                });

                it("should return 500 and error message for runtime exception in filter", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/error")) {
                        assertThat(response.code(), equalTo(500));
                        assertThat(response.body().string(), containsString("Server Error"));
                        assertThat(se.toString(), containsString("appHandlingException exception: Deliberate exception in filter"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("Static file serving tests", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {{
                        serveStaticFiles("/static", "src/test/resources/static");
                    }}.start();
                });

                it("should serve a static file correctly", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/test.txt")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("This is a test file."));
                    }
                });

                it("should return 404 for a non-existent static file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("Not found"));
                    }
                });

                it("should prevent directory traversal attack", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/../../java/com/paulhammant/tinywebserver/TinyWebTest.java")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("Not found"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

        });
    }

    private static @NotNull Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url(url)
                .get().build()).execute();
    }

    private static @NotNull Response httpGet(String url, String hdrKey, String hdrVal) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url(url).addHeader(hdrKey, hdrVal)
                .get().build()).execute();
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(TinyWebTest.class)), new DefaultReporter());
    }
}
