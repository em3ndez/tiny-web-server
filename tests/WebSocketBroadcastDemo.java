package tests;

import com.paulhammant.tnywb.TinyWeb;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.paulhammant.tnywb.TinyWeb.Method.POST;


public class WebSocketBroadcastDemo {

    public static void main(String[] args) {
        TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
            webSocket("/broadcast", new TinyWeb.SocketMessageHandler() {
                @Override
                public void handleMessage(byte[] message, TinyWeb.MessageSender sender) {
                    TinyWeb.SocketMessageHandler theese = this;
                    // This handler doesn't need to do anything for this demo
                }
            });
            endPoint(POST, "/update", (req, rsp, ctx) -> {
                String newVal = ctx.getParam("newValue");
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
