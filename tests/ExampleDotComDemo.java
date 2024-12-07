package tests;

import com.paulhammant.tnywb.TinyWeb;
import com.paulhammant.tnywb.TinyWeb.Request;
import com.paulhammant.tnywb.TinyWeb.Response;
import com.paulhammant.tnywb.TinyWeb.RequestContext;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ExampleDotComDemo {

    private static final Map<String, AtomicInteger> sessionCounters = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        TinyWeb.Server server = new TinyWeb.Server(new InetSocketAddress("example.com", 8080), 8081) {{
            // Serve the JavaScript WebSocket client library
            endPoint(TinyWeb.Method.GET, "/javascriptWebSocketClient.js", new TinyWeb.JavascriptSocketClient());

            // Serve the static HTML/JS page
            endPoint(TinyWeb.Method.GET, "/", (req, res, ctx) -> {
                String sessionId = req.getHeaders().get("Session-ID").getFirst();
                if (sessionId == null || sessionId.isEmpty()) {
                    sessionId = UUID.randomUUID().toString();
                    res.setHeader("Session-ID", sessionId);
                }
                res.setHeader("Content-Type", "text/html");
                res.write("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Counter</title>
                        <script src="/javascriptWebSocketClient.js"></script>
                        <script>
                            const tinyWebSocketClient = new TinyWeb.SocketClient('example.com', 8081);

                            function getSessionId() {
                                let sessionId = localStorage.getItem('sessionId');
                                if (!sessionId) {
                                    sessionId = Math.random().toString(36).substring(2);
                                    localStorage.setItem('sessionId', sessionId);
                                }
                                return sessionId;
                            }

                            async function subscribeToCounter() {
                                const sessionId = getSessionId();

                                await tinyWebSocketClient.waitForOpen();
                                console.log("WebSocket readyState after open:", tinyWebSocketClient.socket.readyState);
                                await tinyWebSocketClient.sendMessage('/ctr', 'Hello WebSocket', {
                                    'Session-ID': sessionId
                                });

                                while (true) {
                                    const response = await tinyWebSocketClient.receiveMessage();
                                    console.log("Received message:", response);
                                    if (response) {
                                        document.getElementById('counter').textContent = response;
                                    }
                                    await new Promise(resolve => setTimeout(resolve, 1000)); // Pause for 1 second
                                }
                            }

                            async function resetCounter() {
                                try {
                                    const sessionId = getSessionId();
                                    const response = await fetch('/resetCtr', {
                                        method: 'PUT',
                                        headers: {
                                            'Session-ID': sessionId
                                        }
                                    });
                                    if (response.ok) {
                                        console.log('Counter reset');
                                    } else {
                                        console.error('Failed to reset counter');
                                    }
                                } catch (error) {
                                    console.error('Error resetting counter:', error);
                                }
                            }

                            subscribeToCounter();
                        </script>
                    </head>
                    <body>
                        <h1>Counter: <span id="counter">0</span></h1>
                        <button onclick="resetCounter()">Reset Counter</button>
                    </body>
                    </html>
                """);
            });

            // WebSocket endpoint to update the counter
            webSocket("/ctr", (message, sender) -> {
                String sessionId = new String(message, StandardCharsets.UTF_8);
                sessionCounters.putIfAbsent(sessionId, new AtomicInteger(0));
                AtomicInteger counter = sessionCounters.get(sessionId);
                    try {
                        int currentCount = counter.incrementAndGet();
                        sender.sendBytesFrame(("" + currentCount).getBytes(StandardCharsets.UTF_8));
                        Thread.sleep(1000); // Pause for 1 second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                    }
            });

            // HTTP PUT endpoint to reset the counter
            endPoint(TinyWeb.Method.PUT, "/resetCtr", (req, res, ctx) -> {
                String sessionId = req.getHeaders().get("Session-ID").getFirst();
                if (sessionId != null && !sessionId.isEmpty()) {
                    AtomicInteger counter = sessionCounters.get(sessionId);
                    if (counter != null) {
                        counter.set(0);
                    }
                }
                res.write("Counter reset", 200);
            });
        }};
        server.start();
    }
}
