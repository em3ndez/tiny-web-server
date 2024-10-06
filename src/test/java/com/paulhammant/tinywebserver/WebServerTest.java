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
import static org.mockito.Mockito.verifyNoInteractions;

@Test
public class WebServerTest {
    TinyWeb.ExampleApp app = Mockito.mock(TinyWeb.ExampleApp.class);
    TinyWeb.Server svr;

    {
        describe("For Example (Tiny) WebServer", () -> {
            describe("Echoing GET endpoint respond with..", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                });
                it("..Jimmy when Jimmy is a param ", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Jimmy")) {
                        assertThat(response.body().string(), equalTo("User profile: Jimmy"));
                    }
                });
                it("..Themla when Thelma is a param ", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Thelma")) {
                        assertThat(response.body().string(), equalTo("User profile: Thelma"));
                    }
                });
                after(() -> {
                    svr.stop();
                    verifyNoInteractions(app);
                });
            });
            describe("Filtering", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                });
                it("Filter that blocks on 'sucks' doesn't block when sucks is absent", () -> {
                    try (Response response = httpGet("http://localhost:8080/foo/bar")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), equalTo("Hello, World!"));
                    }
                });
                it("Filter that blocks on 'sucks' blocks when sucks is present", () -> {
                    try (Response response = httpGet("http://localhost:8080/foo/bar", "sucks", "true")) {
                        assertThat(response.code(), equalTo(403));
                        assertThat(response.body().string(), equalTo("Access Denied"));
                    }
                });
                after(() -> {
                    svr.stop();
                    verifyNoInteractions(app);
                });
            });
            describe("WebServer's directRequest method", () -> {
                before(() -> {
                    svr = TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                });
                it("Can invoke /users/Jimmy endpoint", () -> {
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
                it("should return 404 for a non-existent path", () -> {
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
                after(() -> {
                   svr.stop();
                });
            });
            describe("Static file serving", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], app);
                    svr.start();
                });
                it("should serve a text file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("hello"));
                    }
                });
                it("should return 404 for a non-existent file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("File not found"));
                    }
                });
                it("should serve a file from a subdirectory", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/src/main/java/com/paulhammant/tinywebserver/TinyWeb.java")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("class"));
                    }
                });
                it("should be able to serve a non text file", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/target/classes/com/paulhammant/tinywebserver/TinyWeb$Server.class")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("application/java-vm"));
                        assertThat(response.body().string(), containsString("(Lcom/sun/net/httpserver/HttpExchange;ILjava/lang/String;)V"));
                    }
                });
                after(() -> {
                    svr.stop();
                });
            });
            describe("Path method", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080) {
                        {
                            path("/api", () -> {
                                handle(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, params) -> {
                                    res.write("Parameter: " + params.get("1"));
                                });
                            });
                            path("/api2", () -> {
                                handle(TinyWeb.Method.GET, "/(\\w+)?(.*)", (req, res, params) -> {
                                    System.out.println("QQ test invokes this");
                                    Map<String, String> queryParams = new HashMap<>(req.getQueryParams());
                                    res.write("Parameter: " + params.get("1") + " "+ params.get("2") + " " + queryParams);
                                });
                            });
                        }}
                    .start();
                });
                it("should correctly extract parameters from path", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.GET,
                        "/api/test/123",
                        null,
                        Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Parameter: 123"));
                    assertThat(response.statusCode(), equalTo(200));
                });
                it("two params should not match a one param path", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.GET,
                        "/api/test/123/456",
                        null,
                        Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Not found"));
                    assertThat(response.statusCode(), equalTo(404));
                });
                it("qq test", () -> {
                    TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.GET,
                        "/api2/test/123?a=1&b=2",
                        null,
                        Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("Parameter: test {}"));
                    assertThat(response.statusCode(), equalTo(404));
                });
                after(() -> {
                    svr.stop();
                });
            });
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
                it("invokes ExampleApp method", () -> {
                    try (Response response = httpGet("http://localhost:8080/greeting/A/B")) {
                        assertThat(response.body().string(), equalTo("invoked"));
                    }
                });
                after(() -> {
                    svr.stop();
                    Mockito.verify(app).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<Map<String, String>>any());
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
        runner.run(runner.defineTests(Collections.singletonList(WebServerTest.class)), new DefaultReporter());
    }
}
