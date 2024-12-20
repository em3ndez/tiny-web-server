package tests;

import com.paulhammant.tnywb.TinyWeb;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.paulhammant.tnywb.TinyWeb.Method.POST;


public class WebSocketBroadcastDemo {

    public static class Broadcaster extends ConcurrentLinkedQueue<TinyWeb.MessageSender> {

        ConcurrentLinkedQueue<TinyWeb.MessageSender> closed;

        public void broadcast(String newVal) {
            if (closed != null) {
                this.removeAll(closed);
            }
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            closed = new ConcurrentLinkedQueue<>();
            this.forEach((handler) -> {
                executor.execute(() -> {
                    try {
                        handler.sendBytesFrame(newVal.getBytes());
                    } catch (TinyWeb.ServerException e) {
                        if (e.getCause() instanceof SocketException && e.getCause().getMessage().equals("Socket closed")) {
                            closed.add(handler);
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                });
            });
        }
    }

    public static void main(String[] args) {

        // note: single instance
        Broadcaster broadcaster = new Broadcaster();

        long startTime = System.currentTimeMillis();

        AtomicInteger restartedClients = new AtomicInteger(0);
        AtomicInteger unexpectedClientExceptions = new AtomicInteger(0);

        TinyWeb.Server server = new TinyWeb.Server(TinyWeb.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{

            webSocket("/keepMeUpdatedPlease", (message, sender, ctx) -> {
                broadcaster.add(sender);
            });

            endPoint(POST, "/update", (req, rsp, ctx) -> {
                broadcaster.broadcast(ctx.getParam("newValue"));
            });

        }};
        server.start();

        // Concurrent map to store message counts for each client
        ConcurrentHashMap<Integer, Integer> clientMessageCounts = new ConcurrentHashMap<>();

        // Launch 10 clients
        for (int i = 0; i < 20000; i++) {
            int clientId = i;
            Thread.ofVirtual().start(() -> {
                while (true) {
                    try {
                        TinyWeb.SocketClient client = new TinyWeb.SocketClient("localhost", 8081, "http://localhost:8080");
                        client.performHandshake();
                        client.sendMessage("/keepMeUpdatedPlease", "Client " + clientId + " connecting");

                        boolean shouldStop = client.receiveMessages("stop", message -> {
                            clientMessageCounts.merge(clientId, 1, Integer::sum);
                        });

                        client.close();

                        if (shouldStop) {
                            break; // Exit
                        }
                    } catch (IOException e) {
                        unexpectedClientExceptions.incrementAndGet();
                    }
                    restartedClients.incrementAndGet();
                }
            });
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            broadcaster.broadcast("Broadcast message at " + System.currentTimeMillis());
        }, 0, 1, TimeUnit.SECONDS);

        sleepMillis(150);
        // Schedule a task to print the message counts every 10 seconds
        scheduler.scheduleAtFixedRate(() -> {
            int clientCount = clientMessageCounts.size();
            double average = clientMessageCounts.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf("Elapsed time %d secs: ave message count per ws client: %.2f (Clients: %d initial, %d reconnects, %d excpts)%n", elapsedTime, average, clientCount, restartedClients.get(), unexpectedClientExceptions.get());
        }, 0, 10, TimeUnit.SECONDS);

        System.out.println("WebSocket server started on ws://localhost:8081/broadcast");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void sleepMillis(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }
}
