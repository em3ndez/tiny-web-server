package tests;

import com.paulhammant.tnywb.TinyWeb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.paulhammant.tnywb.TinyWeb.Method.POST;


public class WebSocketBroadcastDemo {

    public static class Broadcaster extends ArrayList<TinyWeb.MessageSender> {

        public void broadcast(String newVal) {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            this.forEach((handler) -> {
                executor.execute(() -> {
                    handler.sendBytesFrame(newVal.getBytes());
                });
            });
        }
    }

    public static void main(String[] args) {

        Broadcaster b = new Broadcaster();

        TinyWeb.Server server = new TinyWeb.Server(8080, 8081) {{
            webSocket("/broadcast", new TinyWeb.SocketMessageHandler() {
                @Override
                public void handleMessage(byte[] message, TinyWeb.MessageSender sender, TinyWeb.RequestContext ctx) {
                    TinyWeb.SocketMessageHandler theese = this;
                    Broadcaster b = ctx.dep(Broadcaster.class);
                    b.add(sender);
                }
            });

            endPoint(POST, "/update", (req, rsp, ctx) -> {
                ctx.dep(Broadcaster.class).broadcast(ctx.getParam("newValue"));
            });

        }};
        server.start();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            b.broadcast("Broadcast message at " + System.currentTimeMillis());
        }, 0, 1, TimeUnit.SECONDS);

        System.out.println("WebSocket server started on ws://localhost:8081/broadcast");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
