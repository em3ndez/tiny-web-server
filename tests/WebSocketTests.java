package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.*;

@Test
public class WebSocketTests {
    TinyWeb.Server webServer;
    TinyWeb.SocketClient client;
    TinyWeb.SocketServer webSocketServer;

    {
        describe("When using standalone TinyWeb.SocketServer without TinyWeb.Server", () -> {

            before(() -> {
                webSocketServer = new TinyWeb.SocketServer(8081) {{
                    registerMessageHandler("/foo/baz", (message, sender) -> {
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
                client = new TinyWeb.SocketClient("localhost", 8081);
                client.performHandshake();
            });

            it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {

                // Example client usage
                client.sendMessage("/foo/baz", "Hello WebSocket");

                StringBuilder messages = new StringBuilder();

                client.receiveMessages("stop", response -> {
                    messages.append(response);
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

        describe("When using TinyWeb.SocketServer with TinyWeb.Server and a contrived webSocket endpoint", () -> {

            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081) {{
                    path("/foo", () -> {
                        endPoint(TinyWeb.Method.GET, "/bar", (req, res, ctx) -> {
                            res.write("OK");
                        });

                        webSocket("/baz", (messageBytes, sender) -> {
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
                client = new TinyWeb.SocketClient("localhost", 8081);
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
                });

                assertThat(messages.toString(),
                        equalTo(
                        "Server sent: Hello WebSocket: 5 -6" +
                                "Server sent: Hello WebSocket: 5 -7" +
                                "Server sent: Hello WebSocket: 5 -8"));

            });
            after(() -> {
                client.close();
                client = null;
                webServer.stop();
                webServer = null;
            });
        });
        describe("When using standalone TinyWeb.SocketServer without TinyWeb.Server", () -> {

            before(() -> {
                webSocketServer = new TinyWeb.SocketServer(8081) {{
                    registerMessageHandler("/foo/baz", (message, sender) -> {
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
                client = new TinyWeb.SocketClient("localhost", 8081);
                client.performHandshake();
            });

            it("Then it should echo three messages plus -1 -2 -3 back to the client", () -> {

                // Example client usage
                client.sendMessage("/foo/baz", "Hello WebSocket");

                StringBuilder messages = new StringBuilder();

                // Read all three response frames
                client.receiveMessages("stop", response -> {
                    messages.append(response);
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
    }

}
