package tests;

import com.paulhammant.tnywb.TinyWeb;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.paulhammant.tnywb.TinyWeb.Method.POST;


public class WebSocketBroadcastDemo {

    public static class Broadcaster extends ArrayList<TinyWeb.MessageSender> {

        public void broadcast(String newVal) {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            ArrayList<TinyWeb.MessageSender> closed = new ArrayList<>();
            this.forEach((handler) -> {
                executor.execute(() -> {
                    try {
                        handler.sendBytesFrame(newVal.getBytes());
                    } catch (TinyWeb.ServerException e) {
                        if (e.getCause() instanceof SocketException && e.getMessage().contains("Socket closed")) {
                            closed.add(handler);
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                });
            });
            this.removeAll(closed);
        }
    }

    public static void main(String[] args) {

        Broadcaster broadcaster = new Broadcaster();

        TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
            webSocket("/broadcasts", new TinyWeb.SocketMessageHandler() {
                @Override
                public void handleMessage(byte[] message, TinyWeb.MessageSender sender, TinyWeb.RequestContext ctx) {
                    broadcaster.add(sender);
                }
            });

            endPoint(POST, "/update", (req, rsp, ctx) -> {
                broadcaster.broadcast(ctx.getParam("newValue"));
            });

        }};
        server.start();

        // Concurrent map to store message counts for each client
        ConcurrentHashMap<Integer, Integer> clientMessageCounts = new ConcurrentHashMap<>();

        // Launch 10 clients
        for (int i = 0; i < 10; i++) {
            int clientId = i;
            new Thread(() -> {
                try {
                    TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081);
                    client.performHandshake();
                    client.sendMessage("/broadcasts", "Client " + clientId + " connected");

                    client.receiveMessages("stop", message -> {
                        clientMessageCounts.merge(clientId, 1, Integer::sum);
                    });

                    client.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }


        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            broadcaster.broadcast("Broadcast message at " + System.currentTimeMillis());
        }, 0, 1, TimeUnit.SECONDS);

        // Schedule a task to print the message counts every 10 seconds
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Current message counts per client:");
            clientMessageCounts.forEach((clientId, count) -> {
                System.out.println("Client " + clientId + ": " + count);
            });
        }, 0, 10, TimeUnit.SECONDS);

        System.out.println("WebSocket server started on ws://localhost:8081/broadcast");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
