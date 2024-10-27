//package com.paulhammant.tinywebserver;

//import jakarta.websocket.*;
import okhttp3.OkHttpClient;
import okhttp3.Response;
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

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static okhttp3.Request.*;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Test
public class TinyWebTest {
    TinyWeb.ExampleApp mockApp;
    TinyWeb.Server svr;
    TinyWeb.SocketServer webSocketServer;

    {
        describe("When using the ExampleApp server via sockets", () -> {
            describe("and accessing the Echoing GET endpoint", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], new TinyWeb.ExampleApp());
                    svr.start();
                });
                it("returns the user profile for Jimmy", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Jimmy")) {
                        assertThat(response.body().string(), equalTo("User profile: Jimmy"));
                    }
                });
                it("returns the user profile for Thelma", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Thelma")) {
                        assertThat(response.body().string(), equalTo("User profile: Thelma"));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("and accessing a nested path with parameters", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        final StringBuilder sb = new StringBuilder();  // don't do this - one sv instance for all incoming connections
                        path("/api", () -> {
                            sb.append("/api->"); // called once only while composing webapp
                            path("/v1", () -> {
                                sb.append("/v1->"); // called once only while composing webapp
                                endPoint(TinyWeb.Method.GET, "/items/(\\w+)/details/(\\w+)", (req, res, ctx) -> {
                                    sb.append("itemz."); // called while handling request
                                    res.write("Item: " + ctx.getParam("1") + ", Detail: " + ctx.getParam("2") + "\n" + sb);
                                });
                            });
                        });
                    }}.start();
                });

                it("extracts parameters correctly from the nested path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/v1/items/123/details/456")) {
                        assertThat(response.body().string(), equalTo("Item: 123, Detail: 456\n/api->/v1->itemz."));
                        assertThat(response.code(), equalTo(200));
                    }
                    try (Response response = httpGet("http://localhost:8080/api/v1/items/abc/details/def")) {
                        assertThat(response.body().string(), equalTo("Item: abc, Detail: def\n/api->/v1->itemz.itemz."));
                        assertThat(response.code(), equalTo(200));
                    }
                });

                it("returns 404 for an incorrect nested path", () -> {
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
            describe("and applying filters", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], new TinyWeb.ExampleApp());
                    svr.start();
                });
                it("allows access when the 'sucks' header is absent", () -> {
                    try (Response response = httpGet("http://localhost:8080/foo/bar")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), equalTo("Hello, World!"));
                    }
                });
                it("denies access when the 'sucks' header is present", () -> {
                    try (Response response = httpGet("http://localhost:8080/foo/bar", "sucks", "true")) {
                        assertThat(response.body().string(), equalTo("Access Denied"));
                        assertThat(response.code(), equalTo(403));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("and serving static files", () -> {
                before(() -> {
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], new TinyWeb.ExampleApp());
                    svr.start();
                });
                it("returns 200 and serves a text file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("text/markdown"));
                        assertThat(response.body().string(), containsString("GPT estimates the path coverage for the TinyWeb class to be around 85-90%"));
                    }
                });
                it("returns 404 for non-existent files", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("Not found"));
                    }
                });
                it("returns 200 and serves a file from a subdirectory", () -> {
                    // Assuming there's a file at src/test/resources/static/subdir/test.txt
                    try (Response response = httpGet("http://localhost:8080/static/TinyWeb.java")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().contentType().toString(), equalTo("text/x-java"));
                        assertThat(response.body().string(), containsString("class"));
                    }
                });
                it("returns 200 and serves a non-text file", () -> {
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

        describe("When using the ExampleApp with Mockito", () -> {
            describe("and accessing the Greeting GET endpoint", () -> {
                before(() -> {
                    mockApp = Mockito.mock(TinyWeb.ExampleApp.class);
                    svr =  TinyWeb.ExampleApp.exampleComposition(new String[0], mockApp);
                    //waitForPortToBeClosed("localhost",8080, 8081);
                    svr.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<TinyWeb.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(mockApp).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<TinyWeb.RequestContext>any());
                });
                it("invokes the ExampleApp foobar method", () -> {
                    try (Response response = httpGet("http://localhost:8080/greeting/A/B")) {
                        assertThat(response.body().string(), equalTo("invoked"));
                    }
                });
                after(() -> {
                    svr.stop();
                    Mockito.verify(mockApp).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<TinyWeb.RequestContext>any());
                    svr = null;
                });
            });
        });

        describe("When testing the application inlined in Cuppa", () -> {
            describe("and the endpoint can extract parameters", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                            path("/api", () -> {
                                endPoint(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, ctx) -> {
                                    res.write("Parameter: " + ctx.getParam("1"));
                                });
                            });
                        }}.start();
                });
                it("extracts parameters correctly from the path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/test/123")) {
                        assertThat(response.body().string(), equalTo("Parameter: 123"));
                        assertThat(response.code(), equalTo(200));
                    }
                });
                it("returns 404 when two parameters are provided for a one-parameter path", () -> {
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
            describe("and the endpoint can extract query parameters", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        path("/api2", () -> {
                            endPoint(TinyWeb.Method.GET, "/test/(\\w+)", (req, res, ctx) -> {
                                res.write("Parameter: " + ctx.getParam("1") + " " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });
                it("handles query parameters correctly", () -> {
                    try (Response response = httpGet("http://localhost:8080/api2/test/123?a=1&b=2")) {
                        assertThat(response.body().string(), equalTo("Parameter: 123 {a=1, b=2}"));
                        assertThat(response.code(), equalTo(200));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("and endpoint and filters can depend on components", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            //deps([OrderBook.class]);
                            endPoint(TinyWeb.Method.GET, "/howManyOrderInBook", (req, res, ctx) -> {
                                OrderBook ob = ctx.dep(OrderBook.class);
                                res.write("Orders: " + ob.orderCount());
                            });
                        });
                        start();
                    }

                        @Override
                        public <T> T instantiateDep(Class<T> clazz, Map<Class<?>, Object> deps) {
                            return (T) new OrderBook();
                        }
                    };
                });
                it("extracts parameters correctly from the path", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/howManyOrderInBook")) {
                        assertThat(response.body().string(), equalTo("Orders: 3"));
                        assertThat(response.code(), equalTo(200));
                    }
                });
                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("and an application exception is thrown from an endpoint", () -> {
                final StringBuilder se = new StringBuilder();
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            endPoint(TinyWeb.Method.GET, "/error", (req, res, ctx) -> {
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

                it("returns 500 and an error message for a runtime exception", () -> {
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
            describe("and the endpoint has query-string parameters", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            endPoint(TinyWeb.Method.GET, "/query", (req, res, ctx) -> {
                                res.write("Query Params: " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });

                it("parses query parameters correctly", () -> {
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

            describe("and response headers are sent to the client", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, -1) {{
                        path("/api", () -> {
                            endPoint(TinyWeb.Method.GET, "/header-test", (req, res, ctx) -> {
                                res.setHeader("X-Custom-Header", "HeaderValue");
                                res.write("Header set");
                            });
                        });
                    }}.start();
                });

                it("sets the custom header correctly", () -> {
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

            describe("and an exception is thrown from a filter", () -> {
                final StringBuilder se = new StringBuilder();
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            filter(TinyWeb.Method.GET, "/error", (req, res, ctx) -> {
                                throw new RuntimeException("Deliberate exception in filter");
                            });
                            endPoint(TinyWeb.Method.GET, "/error", (req, res, ctx) -> {
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

                it("returns 500 and an error message for a runtime exception in a filter", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/error")) {
                        assertThat(response.body().string(), containsString("Server Error"));
                        assertThat(response.code(), equalTo(500));
                        assertThat(se.toString(), containsString("appHandlingException exception: Deliberate exception in filter"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("and testing static file serving", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        serveStaticFiles("/static", ".");
                    }}.start();
                });

                it("serves a static file correctly", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("Cuppa-Framework"));
                    }
                });

                it("returns 404 for a non-existent static file", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/nonexistent.txt")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("Not found"));
                    }
                });

                it("prevents directory traversal attacks", () -> {
                    try (Response response = httpGet("http://localhost:8080/static/../../anythingt.java")) {
                        assertThat(response.code(), equalTo(404));
                        assertThat(response.body().string(), containsString("Not found"));
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("and using TinyWeb.SocketServer without TinyWeb.Server", () -> {

                before(() -> {
                    webSocketServer = new TinyWeb.SocketServer(8081) {{
                        registerMessageHandler("/foo/baz", (message, sender) -> {
                            for (int i = 1; i <= 3; i++) {
                                String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                                sender.sendTextFrame(responseMessage.getBytes("UTF-8"));
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        });
                    }};
                    Thread serverThread = new Thread(webSocketServer::start);
                    serverThread.start();
                });

                it("echoes three messages plus -1 -2 -3 back to the client", () -> {
                    try {
                        Thread.sleep(1000); // Wait for server startup
                    } catch (InterruptedException e) {
                    }

                    // Example client usage
                    try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
                        client.performHandshake();
                        client.sendMessage("/foo/baz", "Hello WebSocket");

                        StringBuilder sb = new StringBuilder();

                        // Read all three response frames
                        for (int i = 0; i < 3; i++) {
                            String response = client.receiveMessage();
                            if (response != null) {
                                sb.append(response);
                            }
                        }
                        assertThat(sb.toString(), equalTo(
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

            describe("and using TinyWeb.SocketServer with TinyWeb.Server", () -> {

                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        path("/foo", () -> {
                            endPoint(TinyWeb.Method.GET, "/bar", (req, res, ctx) -> {
                                res.write("OK");
                            });

                            webSocket("/baz", (message, sender) -> {
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
                    }}.start();
                });

                it("echoes three messages plus -1 -2 -3 back to the client", () -> {
                    try {
                        Thread.sleep(1000); // Wait for server startup
                    } catch (InterruptedException e) {
                    }

                    try (Response response = httpGet("http://localhost:8080/foo/bar")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), equalTo("OK"));
                    }

                    // Example client usage
                    try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
                        client.performHandshake();
                        client.sendMessage("/foo/baz", "Hello WebSocket");

                        StringBuilder sb = new StringBuilder();

                        // Read all three response frames
                        for (int i = 0; i < 3; i++) {
                            String response = client.receiveMessage();
                            if (response != null) {
                                sb.append(response);
                            }
                        }
                        assertThat(sb.toString(), equalTo(
                                "Server sent: Hello WebSocket-1" +
                                        "Server sent: Hello WebSocket-2" +
                                        "Server sent: Hello WebSocket-3"));

                    }

                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });
            describe("and using Selenium to subscribe in a browser", () -> {

                before(() -> {
                    svr = new TinyWeb.Server(8080, 8081) {{
                        endPoint(TinyWeb.Method.GET, "/javascriptWebSocketClient.js", new TinyWeb.JavascriptSocketClient());

                        endPoint(TinyWeb.Method.GET, "/", (req, res, ctx) -> {
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
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        });
                    }}.start();
                });

                it("echoes three messages plus -1 -2 -3 back to the client", () -> {

//                try {
//                    Thread.sleep(600 * 1000);
//                } catch (InterruptedException e) {
//                }

                    WebDriver driver = new ChromeDriver();
                    try {
                        driver.get("http://localhost:8080/");
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                        WebElement messageElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("messageDisplay")));

                        String expectedMessages = "Server sent: Hello WebSocket-1\n" +
                                "Server sent: Hello WebSocket-2\n" +
                                "Server sent: Hello WebSocket-3";
                        assertThat(messageElement.getText(), equalTo(expectedMessages));
                    } finally {
                        driver.quit();
                    }
                });

                after(() -> {
                    svr.stop();
                    svr = null;
                });
            });

            describe("and passing attributes from filter to endpoint", () -> {
                before(() -> {
                    svr = new TinyWeb.Server(8080, -1) {{
                        path("/api", () -> {
                            filter(".*", (req, res, ctx) -> {
                                String allegedlyLoggedInCookie = req.getCookie("logged-in");
                                // This test class only performs rot47 pn the coolie passed in.
                                // That's not in the secure in the slightest. See https://rot47.net/
                                Authentication auth = IsEncryptedByUs.decrypt(allegedlyLoggedInCookie);
                                if (auth.authentic == false) {
                                    res.write("Try logging in again", 403);
                                    return false;
                                } else {
                                    ctx.setAttribute("user", auth.user);
                                }
                                return true; // Continue processing
                            });
                            endPoint(TinyWeb.Method.GET, "/attribute-test", (req, res, ctx) -> {
                                res.write("User Is logged in: " + ctx.getAttribute("user"));
                            });
                        });
                        start();
                    }};
                });

                it("attribute user was passed from filter to endPoint for authentic user", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/attribute-test", "Cookie", "logged-in=7C65o6I2>A=6]4@>")) {
                        assertThat(response.body().string(), equalTo("User Is logged in: fred@example.com"));
                        assertThat(response.code(), equalTo(200));
                    }
                });

                it("attribute user was not passed from filter to endPoint for inauthentic user", () -> {
                    try (Response response = httpGet("http://localhost:8080/api/attribute-test", "Cookie", "logged-in=aeiouaeiou;")) {
                        assertThat(response.body().string(), equalTo("Try logging in again"));
                        assertThat(response.code(), equalTo(403));
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

    public static class OrderBook {

        public int orderCount() {
            return 3;
        }
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(TinyWebTest.class)), new DefaultReporter());
    }

}

