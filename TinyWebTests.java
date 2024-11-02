/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) Paul Hammant, 2024
 */

package com.paulhammant.tnywb.tests;

import com.paulhammant.tnywb.TinyWeb.Request;
import com.paulhammant.tnywb.TinyWeb.Response;
import okhttp3.OkHttpClient;

import org.mockito.Mockito;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static java.lang.Thread.sleep;
import static okhttp3.Request.*;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


import com.paulhammant.tnywb.TinyWeb;
import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.FilterResult.STOP;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static com.paulhammant.tnywb.TinyWeb.Method.POST;
import static com.paulhammant.tnywb.TinyWeb.Method.PUT;

@Test
public class TinyWebTests {
    ExampleApp exampleApp;
    TinyWeb.Server webServer;
    TinyWeb.SocketServer webSocketServer;

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
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When serving static files", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        serveStaticFiles("/static", new File(".").getAbsolutePath());
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
                    exampleApp = Mockito.mock(ExampleApp.class);
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        // some of these are not used by the it() tests
                        endPoint(GET, "/greeting/(\\w+)/(\\w+)", exampleApp::foobar);
                    }};
                    //waitForPortToBeClosed("localhost",8080, 8081);
                    webServer.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<TinyWeb.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(exampleApp).foobar(Mockito.any(Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<TinyWeb.RequestContext>any());
                });
                it("Then it should invoke the ExampleApp foobar method", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/greeting/A/B"),
                            "invoked", 200);

                });
                after(() -> {
                    webServer.stop();
                    Mockito.verify(exampleApp).foobar(Mockito.any(Request.class),
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
                        this.put(ProductInventory.class, new ProductInventory(/* would have secrets in real usage */));
                    }}){

                        // This is not Dependency Injection
                        // This also does not use reflection so is fast.

                        @Override
                        public <T> T  instantiateDep(Class<T> clazz, TinyWeb.ComponentCache requestCache) {
                            if (clazz == ShoppingCart.class) {
                                return (T) createOrGetShoppingCart(requestCache);
                            }
                            throw new IllegalArgumentException("Unsupported class: " + clazz);
                        }

                    });
                    //svr.applicationScopeCache.put()
                    doCompositionForOneTest(webServer);
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
                        serveStaticFiles("/static", ".");
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
            describe("When using standalone TinyWeb.SocketServer without TinyWeb.Server", () -> {

                before(() -> {
                    webSocketServer = new TinyWeb.SocketServer(8081) {{
                        registerMessageHandler("/foo/baz", (message, sender) -> {
                            for (int i = 1; i <= 3; i++) {
                                String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                                sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                                try {
                                    sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        });
                    }};
                    Thread serverThread = new Thread(webSocketServer::start);
                    serverThread.start();
                });

                it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {
                    try {
                        sleep(1000); // Wait for server startup
                    } catch (InterruptedException e) {
                    }

                    // Example client usage
                    try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
                        client.performHandshake();
                        client.sendMessage("/foo/baz", "Hello WebSocket");

                        StringBuilder messages = new StringBuilder();

                        // Read all three response frames
                        for (int i = 0; i < 3; i++) {
                            String response = client.receiveMessage();
                            if (response != null) {
                                messages.append(response);
                            }
                        }
                        assertThat(messages.toString(), equalTo(
                                "Server sent: Hello WebSocket-1" +
                                        "Server sent: Hello WebSocket-2" +
                                        "Server sent: Hello WebSocket-3"));

                    }

                });

                after(() -> {
                    webSocketServer.stop();
                    webSocketServer = null;
                });
            });

            describe("When using TinyWeb.SocketServer with TinyWeb.Server", () -> {

                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        path("/foo", () -> {
                            endPoint(GET, "/bar", (req, res, ctx) -> {
                                res.write("OK");
                            });

                            webSocket("/baz", (message, sender) -> {
                                for (int i = 1; i <= 3; i++) {
                                    String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                                    sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                                    try {
                                        sleep(100);
                                    } catch (InterruptedException e) {
                                    }
                                }
                            });
                        });
                    }}.start();
                });

                it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {
                    try {
                        sleep(1000); // Wait for server startup
                    } catch (InterruptedException e) {}

                    bodyAndResponseCodeShouldBe(httpGet("/foo/bar"),
                            "OK", 200);

                    // Example client usage
                    try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
                        client.performHandshake();
                        client.sendMessage("/foo/baz", "Hello WebSocket");

                        StringBuilder messages = new StringBuilder();

                        // Read all three response frames
                        for (int i = 0; i < 3; i++) {
                            String response = client.receiveMessage();
                            if (response != null) {
                                messages.append(response);
                            }
                        }
                        assertThat(messages.toString(), equalTo(
                                "Server sent: Hello WebSocket-1" +
                                        "Server sent: Hello WebSocket-2" +
                                        "Server sent: Hello WebSocket-3"));
                    }
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When using Selenium to subscribe in a browser", () -> {

                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        endPoint(GET, "/javascriptWebSocketClient.js", new TinyWeb.JavascriptSocketClient());

                        endPoint(GET, "/", (req, res, ctx) -> {
                            res.setHeader("Content-Type", "text/html");
                            res.sendResponse("""
                                <!DOCTYPE html>
                                <html lang="en">
                                <head>
                                    <meta charset="UTF-8">
                                    <title>WebSocket Test</title>
                                    <script src="/javascriptWebSocketClient.js"></script>
                                </head>
                                <body>
                                    <h1>WebSocket Message Display</h1>
                                    <pre id="messageDisplay"></pre>
                                </body>
                                <script>
                                console.log("hello 1");
                                
                                const tinyWebSocketclient = new TinyWeb.SocketClient('localhost', 8081);
                                
                                async function example() {
                                    try {
                                        console.log("WebSocket readyState before open:", tinyWebSocketclient.socket.readyState);
                                        await tinyWebSocketclient.waitForOpen();
                                        console.log("WebSocket readyState after open:", tinyWebSocketclient.socket.readyState);
                                        await tinyWebSocketclient.sendMessage('/baz', 'Hello WebSocket');
                                        
                                        for (let i = 0; i < 3; i++) {
                                            const response = await tinyWebSocketclient.receiveMessage();
                                            console.log("Received message:", response);
                                            if (response) {
                                                document.getElementById('messageDisplay').textContent += (response + "\\n");
                                            }
                                        }
                                        await tinyWebSocketclient.close();
                                    } catch (error) {
                                        console.error('WebSocket error:', error);
                                    }
                                }

                                example();
                                </script>
                                </html>
                            """, 200);
                        });

                        webSocket("/baz", (message, sender) -> {
                            for (int i = 1; i <= 3; i++) {
                                String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                                sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                                try {
                                    sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        });
                    }}.start();
                });

                it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {

                    // To play with the wee browser app, uncomment this, and go to localhost:8080
                    // after kicking off the test suite
//                    try {
//                        Thread.sleep(600 * 1000);
//                    } catch (InterruptedException e) {
//                    }

                    WebDriver driver = new ChromeDriver();
                    try {
                        driver.get("http://localhost:8080/");
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                        WebElement messageElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("messageDisplay")));
                        sleep(500);
                        String expectedMessages = "Server sent: Hello WebSocket-1\n" +
                                "Server sent: Hello WebSocket-2\n" +
                                "Server sent: Hello WebSocket-3";
                        assertThat(messageElement.getText(), equalTo(expectedMessages));
                    } finally {
                        driver.quit();
                    }
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When passing attributes from filter to endpoint", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, -1) {{
                        path("/api", () -> {
                            filter(".*", (req, res, ctx) -> {
                                String allegedlyLoggedInCookie = req.getCookie("logged-in");
                                // This test class only performs rot47 pn the coolie passed in.
                                // That's not in the secure in the slightest. See https://rot47.net/
                                Authentication auth = IsEncryptedByUs.decrypt(allegedlyLoggedInCookie);
                                if (auth.authentic == false) {
                                    res.write("Try logging in again", 403);
                                    return STOP;
                                } else {
                                    ctx.setAttribute("user", auth.user);
                                }
                                return CONTINUE; // Continue processing
                            });
                            endPoint(GET, "/attribute-test", (req, res, ctx) -> {
                                res.write("User Is logged in: " + ctx.getAttribute("user"));
                            });
                        });
                        start();
                    }};
                });

                it("Then the attribute user should be passed from filter to endPoint for authentic user", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/attribute-test", "Cookie", "logged-in=7C65o6I2>A=6]4@>"), "User Is logged in: fred@example.com", 200);
                });

                it("Then the attribute user should not be passed from filter to endPoint for inauthentic user", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/attribute-test", "Cookie", "logged-in=aeiouaeiou;"), "Try logging in again", 403);
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When the composition can happen on a previously instantiated TinyWeb.Server", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        endPoint(GET, "/foo", (req, res, ctx) -> {
                            res.write("Hello1");
                        });
                    }};
                    new TinyWeb.ServerComposition(webServer) {{
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

        });
    }

    private static void bodyAndResponseCodeShouldBe(okhttp3.Response response, String bodyShouldBe, int rcShouldBe) throws IOException {
        try (response) {
            assertThat(response.body().string(), equalTo(bodyShouldBe));
            assertThat(response.code(), equalTo(rcShouldBe));
        }
    }

    public static class ExampleApp {

        public void foobar(Request req, Response res, TinyWeb.RequestContext ctx) {
            res.write(String.format("Hello, %s %s!", ctx.getParam("1"), ctx.getParam("2")));
        }

        public static TinyWeb.Server exampleComposition(String[] args, ExampleApp app) {
            TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{

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
                    webSocket("/eee", (message, sender) -> {
                        for (int i = 1; i <= 3; i++) {
                            String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                            sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                    });
                });

                serveStaticFiles("/static", new File(".").getAbsolutePath());

                endPoint(GET, "/users/(\\w+)", (req, res, ctx) -> {
                    res.write("User profile: " + ctx.getParam("1"));
                });


                endPoint(POST, "/echo", (req, res, ctx) -> {
                    res.write("You sent: " + req.getBody(), 201);
                });

                endPoint(GET, "/greeting/(\\w+)/(\\w+)", app::foobar);

                endPoint(PUT, "/update", (req, res, ctx) -> {
                    res.write("Updated data: " + req.getBody(), 200);
                });

                path("/api", () -> {
                    endPoint(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, ctx) -> {
                        res.write("Parameter: " + ctx.getParam("1"));
                    });
                });
            }};
            return server;
        }
    }
    
    private static void doCompositionForOneTest(TinyWeb.Server svr) {
        new TinyWeb.ServerComposition(svr) {{
            path("/api", () -> {
                //deps([OrderBook.class]);
                endPoint(GET, "/howManyOrderInBook", (req, res, ctx) -> {

                    ShoppingCart sc = ctx.dep(ShoppingCart.class);
                    res.write("Cart Items before: " + sc.cartCount() + "\n" +
                            "apple picked: " + sc.pickItem("apple") + "\n" +
                            "Cart Items after: " + sc.cartCount() + "\n");
                });
            });

        }};
    }

    private static @NotNull okhttp3.Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url("http://localhost:8080" + url)
                .get().build()).execute();
    }

    private static @NotNull okhttp3.Response httpGet(String url, String hdrKey, String hdrVal) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url("http://localhost:8080" + url).addHeader(hdrKey, hdrVal)
                .get().build()).execute();
    }

    public static class IsEncryptedByUs {
        public static Authentication decrypt(String allegedlyLoggedInCookie) {
            String rot47ed = rot47(allegedlyLoggedInCookie);
            // check is an email address
            if (rot47ed.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$")) {
                return new Authentication(true, rot47ed);
            } else {
                return new Authentication(false, null);
            }
        }
    }
    public record Authentication(boolean authentic, String user) {}

    private static String rot47(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= '!' && c <= 'O') {
                result.append((char) (c + 47));
            } else if (c >= 'P' && c <= '~') {
                result.append((char) (c - 47));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }



    public static class ProductInventory {

        // We should not hard code cart contents in real life - see note about database below.
        Map<String, Integer> stockItems = new HashMap<>() {{
            put("apple", 100);
            put("orange", 50);
            put("bagged bannana", 33);
        }};

        public boolean customerReserves(String item) {
            if (stockItems.containsKey(item)) {
                if (stockItems.get(item) > 0) {
                    stockItems.put(item, stockItems.get(item) -1);
                    return true;
                }
            }
            return false;
        }
    }

    /*
      Most likely the real version of this would use a database to go get the shopping cart contents
      for the user. Or some database-like solution, that aids quick "session" re-acquisition.
     */
    public static class ShoppingCart {

        private final ProductInventory inv;
        private final Map<String, Integer> items = new HashMap<>();

        public ShoppingCart(ProductInventory inv) {
            this.inv = inv;
        }

        public int cartCount() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean pickItem(String item) {
            boolean gotIt = inv.customerReserves(item);
            if (!gotIt) {
                return false;
            }
            if (items.containsKey(item)) {
                items.put(item, items.get(item) +1);
            } else {
                items.put(item, 1);
            }
            return true;
        }
    }



    public static ShoppingCart createOrGetShoppingCart(TinyWeb.ComponentCache cache) {
        return cache.getOrCreate(ShoppingCart.class, () ->
                new ShoppingCart(getOrCreateProductInventory(cache))
        );
    }

    public static ProductInventory getOrCreateProductInventory(TinyWeb.ComponentCache cache) {
        return cache.getParent().getOrCreate(ProductInventory.class, ProductInventory::new);
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(TinyWebTests.class)), new DefaultReporter());
    }

}

