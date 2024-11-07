package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class WebSocketTests {
    TinyWeb.Server webServer;
    TinyWeb.SocketServer webSocketServer;

    {
        describe("When using standalone TinyWeb.SocketServer without TinyWeb.Server", () -> {

            before(() -> {
                webSocketServer = new TinyWeb.SocketServer(8081) {{
                    registerMessageHandler("/foo/baz", (message, sender) -> {
                        for (int i = 1; i <= 3; i++) {
                            String responseMessage = "Server sent: " + new String(message, "UTF-8") + "-" + i;
                            sender.sendBytesFrame(responseMessage.getBytes("UTF-8"));
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

            it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {
                try {
                    Thread.sleep(1000); // Wait for server startup
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

        describe("When using TinyWeb.SocketServer with TinyWeb.Server and a contrived webSocket endpoint", () -> {

            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081) {{
                    path("/foo", () -> {
                        endPoint(TinyWeb.Method.GET, "/bar", (req, res, ctx) -> {
                            res.write("OK");
                        });

                        webSocket("/baz", (messageBytes, sender) -> {
                            for (int i = 1; i <= 3; i++) {
                                String message = new String(messageBytes, "UTF-8");
                                int num = Integer.parseInt(message.split(": ")[1]);
                                String responseMessage = "Server sent: " + message + " -" + (i+num);
                                sender.sendBytesFrame(responseMessage.getBytes("UTF-8"));
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        });
                    });
                }}.start();
            });

            it("Then it should echo three modified messages back to the client (twice)", () -> {
                try {
                    Thread.sleep(1000); // Wait for server startup
                } catch (InterruptedException e) {}

                bodyAndResponseCodeShouldBe(httpGet("/foo/bar"),
                        "OK", 200);

                // Example client usage
                try (TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081)) {
                    client.performHandshake();
                    client.sendMessage("/foo/baz", "Hello WebSocket: 0");

                    StringBuilder messages = new StringBuilder();

                    // Read all three response frames
                    for (int i = 0; i < 3; i++) {
                        String response = client.receiveMessage();
                        if (response != null) {
                            messages.append(response);
                        }
                    }
                    assertThat(messages.toString(),
                            equalTo(
                            "Server sent: Hello WebSocket: 0 -1" +
                                    "Server sent: Hello WebSocket: 0 -2" +
                                    "Server sent: Hello WebSocket: 0 -3"));

                    client.sendMessage("/foo/baz", "Hello WebSocket: 5");

                    messages = new StringBuilder();

                    // Read all three response frames
                    for (int i = 0; i < 3; i++) {
                        String response = client.receiveMessage();
                        if (response != null) {
                            messages.append(response);
                        }
                    }

                    assertThat(messages.toString(),
                            equalTo(
                            "Server sent: Hello WebSocket: 5 -6" +
                                    "Server sent: Hello WebSocket: 5 -7" +
                                    "Server sent: Hello WebSocket: 5 -8"));
                }
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }

}
