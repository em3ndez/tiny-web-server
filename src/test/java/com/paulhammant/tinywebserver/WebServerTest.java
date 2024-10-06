package com.paulhammant.tinywebserver;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.mockito.Mockito;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.model.TestBlock;
import org.forgerock.cuppa.reporters.DefaultReporter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;

import static okhttp3.Request.*;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Map;

@Test
public class WebServerTest {
    WebServer.ExampleApp app = Mockito.mock(WebServer.ExampleApp.class);
    WebServer svr;

    {
        describe("For Example (Tiny) WebServer", () -> {
            describe("Echoing GET endpoint respond with..", () -> {
                before(() -> {
                    svr =  WebServer.ExampleApp.exampleComposition(new String[0], app);
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
                    Mockito.verifyNoInteractions(app);
                });
            });

            describe("WebServer's directRequest method", () -> {
                before(() -> {
                    svr = WebServer.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                });
                it("Can invoke /users/Jimmy endoint", () -> {
                    WebServer.SimulatedResponse response = svr.directRequest(
                        WebServer.Method.GET,
                        "/users/Jimmy",
                        null,
                        Collections.emptyMap()
                    );
                    assertThat(response.body(), equalTo("User profile: Jimmy"));
                    assertThat(response.statusCode(), equalTo(200));
                    assertThat(response.contentType(), equalTo("text/plain"));
                });
                it("should return 404 for a non-existent path", () -> {
                    WebServer.SimulatedResponse response = svr.directRequest(
                        WebServer.Method.GET,
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
                    svr =  WebServer.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                });
                it("should serve the README.md file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("hello"));
                    }
                });
                after(() -> {
                    svr.stop();
                });
            });
            describe("Greeting GET endpoint", () -> {
                before(() -> {
                    svr =  WebServer.ExampleApp.exampleComposition(new String[0], app);
                    //waitForPortToBeClosed("localhost",8080);
                    svr.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<WebServer.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(app).foobar(Mockito.any(WebServer.Request.class), Mockito.any(WebServer.Response.class), Mockito.<Map<String, String>>any());
                });
                it("invokes ExampleApp method", () -> {
                    try (Response response = httpGet("http://localhost:8080/greeting/A/B")) {
                        assertThat(response.body().string(), equalTo("invoked"));
                    }
                });
                after(() -> {
                    svr.stop();
                    Mockito.verify(app).foobar(Mockito.any(WebServer.Request.class), Mockito.any(WebServer.Response.class), Mockito.<Map<String, String>>any());
                });
            });
        });
    }

    private static @NotNull Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url(url)
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
