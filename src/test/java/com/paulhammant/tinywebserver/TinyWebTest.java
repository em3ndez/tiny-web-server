package com.paulhammant.tinywebserver;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.mockito.Mockito;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Socket;
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
            describe("Static file serving functionality", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    svr.start();
                });
                it("should return 200 and serve a text file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("hello"));
                    }
                });
                it("should return 404 for non-existent files", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("File not found"));
                    }
                });
                it("should return 200 and serve a file from a subdirectory", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/src/main/java/com/paulhammant/tinywebserver/TinyWeb.java")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("class"));
                    }
                });
                it("should return 200 and serve a non-text file", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/target/classes/com/paulhammant/tinywebserver/TinyWeb$Server.class")) {
                        assertThat(response.code(), equalTo(200));
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

        describe("Direct Example WebServer functionality bypassing sockets", () -> {
            before(() -> {
                svr = TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                svr.start();
            });
            describe("POST endpoint", () -> {
                it("should return 201 and echo the request body", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.POST,
                            "/echo",
                            "hello everyone",
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("You sent: hello everyone"));
                    assertThat(response.statusCode(), equalTo(201));
                });
            });
            describe("ExampleApp PUT method", () -> {
                it("should return 200 and update the data", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.PUT,
                            "/update",
                            "new data",
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Updated data: new data"));
                    assertThat(response.statusCode(), equalTo(200));
                });
            });
            describe("Direct request handling", () -> {
                it("should return user profile for Jimmy via direct request", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/users/Jimmy",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("User profile: Jimmy"));
                    assertThat(response.statusCode(), equalTo(200));
                    assertThat(response.contentType(), equalTo("text/plain"));
                });
                it("should return 404 for non-existent paths", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/nonexistent",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Not found"));
                    assertThat(response.statusCode(), equalTo(404));
                    assertThat(response.contentType(), equalTo("text/plain"));
                });
                it("should handle POST requests correctly", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.POST,
                            "/echo",
                            "test post body",
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("You sent: test post body"));
                    assertThat(response.statusCode(), equalTo(201));
                });
                it("should handle PUT requests correctly", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.PUT,
                            "/update",
                            "test put body",
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Updated data: test put body"));
                    assertThat(response.statusCode(), equalTo(200));
                });
                it("should return 405 for unsupported DELETE method", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.DELETE,
                            "/users/Jimmy",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Method not allowed"));
                    assertThat(response.statusCode(), equalTo(405));
                });
                it("should return user profile for Jimmy via direct request", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/users/Jimmy",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("User profile: Jimmy"));
                    assertThat(response.statusCode(), equalTo(200));
                    assertThat(response.contentType(), equalTo("text/plain"));
                });

                it("should return 404 for non-existent paths", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/nonexistent",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Not found"));
                    assertThat(response.statusCode(), equalTo(404));
                    assertThat(response.contentType(), equalTo("text/plain"));
                });

                it("should handle POST requests correctly", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.POST,
                            "/echo",
                            "test post body",
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("You sent: test post body"));
                    assertThat(response.statusCode(), equalTo(201));
                });

                it("should handle PUT requests correctly", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.PUT,
                            "/update",
                            "test put body",
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Updated data: test put body"));
                    assertThat(response.statusCode(), equalTo(200));
                });

                it("should return 405 for unsupported DELETE method", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.DELETE,
                            "/users/Jimmy",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Method not allowed"));
                    assertThat(response.statusCode(), equalTo(405));
                });
            });
            after(() -> {
                svr.stop();
                svr = null;
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
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/api/test/123",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Parameter: 123"));
                    assertThat(response.statusCode(), equalTo(200));
                });
                it("should return 404 when two params are provided for a one param path", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/api/test/123/456",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Not found"));
                    assertThat(response.statusCode(), equalTo(404));
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
                            endPoint(TinyWeb.Method.GET, "/test/(\\w+)?(.*)", (req, res, params) -> {
                                res.write("Parameter: " + params);
                            });
                        });
                    }}.start();
                });
                it("should handle query parameters correctly", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/api2/test/123?a=1&b=2",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Parameter: {1=123, a=1, b=2}"));
                    assertThat(response.statusCode(), equalTo(200));
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
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/static/test.txt",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.statusCode(), equalTo(200));
                    assertThat(response.body(), containsString("This is a test file."));
                });

                it("should return 404 for a non-existent static file", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/static/nonexistent.txt",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.statusCode(), equalTo(404));
                    assertThat(response.body(), containsString("File not found"));
                });

                it("should prevent directory traversal attack", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                            TinyWeb.Method.GET,
                            "/static/../../java/com/paulhammant/tinywebserver/TinyWebTest.java",
                            null,
                            Collections.emptyMap()
                    );
                    assertThat(response.statusCode(), equalTo(404));
                    assertThat(response.body(), containsString("File not found"));
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

    public static void waitForPortToBeClosed(String host, int port) {
        boolean portOpen = true;

        while (portOpen) {
            try (Socket socket = new Socket(host, port)) {
                // Port is still open, wait and try again
                System.out.println("Port " + port + " is still open. Waiting...");
                Thread.sleep(2000); // Wait 2 seconds before trying again
            } catch (IOException e) {
                // Exception indicates that the port is not open
                portOpen = false;
                System.out.println("Port " + port + " is closed.");
            } catch (InterruptedException e) {
            }
        }
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(TinyWebTest.class)), new DefaultReporter());
    }
}
