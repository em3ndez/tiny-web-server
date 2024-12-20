package tests;

import com.paulhammant.tiny.Tiny;
import org.forgerock.cuppa.Test;

import static com.paulhammant.tiny.Tiny.toBytes;
import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.*;

@Test
public class WebSocketTests {
    Tiny.WebServer webServer;
    Tiny.WebSocketClient client;
    Tiny.WebSocketServer webSocketServer;

    {
        describe("When using standalone Tiny.WebSocketServer without Tiny.WebServer", () -> {

            before(() -> {
                webSocketServer = new Tiny.WebSocketServer(Tiny.Config.create().withWebSocketPort(8081)) {{
                    registerMessageHandler("/foo/baz", (message, sender, context) -> {
                        for (int i = 1; i <= 3; i++) {
                            String responseMessage = "Server sent: " + bytesToString(message) + "-" + i;
                            sender.sendBytesFrame(toBytes(responseMessage));
                            try {
                                sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        sender.sendBytesFrame(toBytes("stop"));
                    });
                }};
                Thread serverThread = new Thread(webSocketServer::start);
                serverThread.start();
                Thread.sleep(50);
                client = new Tiny.WebSocketClient("localhost", 8081, "http://localhost:8080");
                client.performHandshake();
            });

            it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {

                // Example client usage
                client.sendMessage("/foo/baz", "Hello WebSocket");

                StringBuilder messages = new StringBuilder();

                client.receiveMessages("stop", response -> {
                    messages.append(response);
                    return true;
                });

                assertThat(messages.toString(), equalTo(
                        "Server sent: Hello WebSocket-1" +
                                "Server sent: Hello WebSocket-2" +
                                "Server sent: Hello WebSocket-3"));

            });

            after(() -> {
                client.close();
                client = null;
                webSocketServer.stop();
                webSocketServer = null;
            });
        });

        describe("When using Tiny.WebSocketServer with Tiny.WebServer and a contrived webSocket endpoint", () -> {

            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080).withWebSocketPort(8081)) {{
                    path("/foo", () -> {
                        endPoint(Tiny.HttpMethods.GET, "/bar", (req, res, ctx) -> {
                            res.write("OK");
                        });

                        webSocket("/baz", (messageBytes, sender, context) -> {
                            for (int i = 1; i <= 3; i++) {
                                String message = bytesToString(messageBytes);
                                int num = Integer.parseInt(message.split(": ")[1]);
                                String responseMessage = "Server sent: " + message + " -" + (i+num);
                                sender.sendBytesFrame(toBytes(responseMessage));
                                try {
                                    sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                            sender.sendBytesFrame(toBytes("stop"));
                        });
                    });
                }}.start();
                Thread.sleep(50);
                client = new Tiny.WebSocketClient("localhost", 8081, "http://localhost:8080");
                client.performHandshake();

            });

            it("Then it should echo three modified messages back to the client (twice)", () -> {

                bodyAndResponseCodeShouldBe(httpGet("/foo/bar"),
                        "OK", 200);

                // Example client usage
                client.sendMessage("/foo/baz", "Hello WebSocket: 0");

                final StringBuilder messages = new StringBuilder();

                client.receiveMessages("stop", response -> {
                    messages.append(response);
                    return true;
                });
                assertThat(messages.toString(),
                        equalTo(
                        "Server sent: Hello WebSocket: 0 -1" +
                                "Server sent: Hello WebSocket: 0 -2" +
                                "Server sent: Hello WebSocket: 0 -3"));

                client.sendMessage("/foo/baz", "Hello WebSocket: 5");

                messages.delete(0, messages.length());

                // Read all three response frames
                client.receiveMessages("stop", (message) -> {
                    messages.append(message);
                    return true;
                });

                assertThat(messages.toString(),
                        equalTo(
                        "Server sent: Hello WebSocket: 5 -6" +
                                "Server sent: Hello WebSocket: 5 -7" +
                                "Server sent: Hello WebSocket: 5 -8"));

            });

            it("Then it should do a 404 equivalent for a missing path", () -> {

                // Example client usage
                client.sendMessage("/foo/doesNotExist", "Hello WebSocket: 0");

                StringBuilder message = new StringBuilder();

                client.receiveMessages("stop", new Tiny.InterruptibleConsumer<String>() {
                    @Override
                    public boolean accept(String response) {
                        message.append(response);
                        return false;
                    }
                });
                assertThat(message.toString(), equalTo("Error: 404"));

            });

            after(() -> {
                webServer.stop();
                client.close();
                client = null;
                webServer = null;
            });
        });

        describe("When using standalone Tiny.WebSocketServer without Tiny.WebServer", () -> {

            before(() -> {
                webSocketServer = new Tiny.WebSocketServer(Tiny.Config.create().withWebSocketPort(8081)) {{
                    registerMessageHandler("/foo/baz", (message, sender, context) -> {
                        for (int i = 1; i <= 3; i++) {
                            String responseMessage = "Server sent: " + bytesToString(message) + "-" + i;
                            sender.sendBytesFrame(toBytes(responseMessage));
                            try {
                                sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        sender.sendBytesFrame(toBytes("stop"));
                    });
                }};
                Thread serverThread = new Thread(webSocketServer::start);
                serverThread.start();
                Thread.sleep(100);
                client = new Tiny.WebSocketClient("localhost", 8081, "https://localhost:8080");
                client.performHandshake();
            });

            it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {

                // Example client usage
                client.sendMessage("/foo/baz", "Hello WebSocket");

                StringBuilder messages = new StringBuilder();

                // Read all three response frames
                client.receiveMessages("stop", response -> {
                    messages.append(response);
                    return true;
                });

                assertThat(messages.toString(), equalTo(
                        "Server sent: Hello WebSocket-1" +
                                "Server sent: Hello WebSocket-2" +
                                "Server sent: Hello WebSocket-3"));

            });

            after(() -> {
                webSocketServer.stop();
                client.close();
                client = null;
                webSocketServer = null;
            });
        });

        /// / eeee

        describe("When mismatching domains on SocketServer client lib", () -> {

            before(() -> {
                webSocketServer = new Tiny.WebSocketServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                    registerMessageHandler("/foo/baz", (message, sender, context) -> {
                        sender.sendBytesFrame(toBytes("hi"));
                        sender.sendBytesFrame(toBytes("stop"));
                    });
                }};
                Thread serverThread = new Thread(webSocketServer::start);
                serverThread.start();
                Thread.sleep(50);
                client = new Tiny.WebSocketClient("localhost", 8081, "http://example:8080");
                client.performHandshake();
            });

            it("Conversation is vetoed", () -> {

                // Example client usage
                client.sendMessage("/foo/baz", "Hello WebSocket");

                StringBuilder messages = new StringBuilder();

                client.receiveMessages("stop", response -> {
                    messages.append(response);
                    return false;
                });

                assertThat(messages.toString(), equalTo("Error: Bad Origin"));

            });

            after(() -> {
                client.close();
                client = null;
                webSocketServer.stop();
                webSocketServer = null;
            });
        });

    }

}
