package tests;

import com.paulhammant.tnywb.TinyWeb;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocketBroadcastDemo {

    public static void main(String[] args) {
        TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
            webSocket("/broadcast", (message, sender) -> {
                // This handler doesn't need to do anything for this demo
            });
        }};
        server.start();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            String broadcastMessage = "Broadcast message at " + System.currentTimeMillis();
            server.socketServer.messageHandlers.forEach((path, handler) -> {
                if (path.equals("/broadcast")) {
                    server.socketServer.executor.execute(() -> {
                        try {
                            handler.handleMessage(broadcastMessage.getBytes(), new TinyWeb.MessageSender(System.out));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            });
        }, 0, 1, TimeUnit.SECONDS);

        System.out.println("WebSocket server started on ws://localhost:8081/broadcast");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
