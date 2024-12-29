package tests;

import com.paulhammant.tiny.Tiny;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

public class SSEPerformanceTest {

    public static class SseStat {

        public boolean serverConnectException;
        public boolean serverIOE;
        public boolean connected;
        public boolean connecting;
        public int messagesReceived;
        public int messagesAsExpected;
        public int clientIOE;
    }

    public static void main(String[] args) {

        Map<Integer, SseStat> stats = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();

        Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebBacklog(3000)) {
            @Override
            protected HttpServer makeHttpServer() throws IOException {
                return super.makeHttpServer();
            }

            @Override
            protected void exceptionDuringHandling(Throwable e, HttpExchange exchange) {
                System.out.println(e.getMessage());
                e.printStackTrace();
                // none observed
            }
            @Override
            protected void serverException(Tiny.ServerException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
                // none observed
            }

            {
            endPoint(Tiny.HttpMethods.GET, "/sse", (req, res, ctx) -> {
                res.setHeader("Content-Type", "text/event-stream");
                res.setHeader("Cache-Control", "no-cache");
                res.setHeader("Connection", "keep-alive");
                String client = req.getHeaders().get("sse_perf_client").getFirst();
                System.out.println("sse_perf_client " + client + " " + ctx.getAttribute("connextion"));
                try {
                    res.sendResponseHeaders(200, 0);
                    OutputStream outputStream = res.getResponseBody();
                    int i = 0;
                    while (true) {
                        outputStream.write(("data: Message " + i++ + "\n\n").getBytes());
                        outputStream.flush();
                        try {
                            Thread.sleep(new java.util.Random().nextInt(2001)); // average 1 second
                        } catch (InterruptedException e) {
                        }
                    }
                } catch (ConnectException e) {
                    SseStat stat = stats.get(Integer.parseInt(client));
                    if (stat == null) {
                        stat = new SseStat();
                        stats.put(Integer.parseInt(client), stat);
                    }
                    stat.serverConnectException = true;
                } catch (IOException e) {
                    SseStat stat = stats.get(Integer.parseInt(client));
                    if (stat == null) {
                        stat = new SseStat();
                        stats.put(Integer.parseInt(client), stat);
                    }
                    stat.serverIOE = true;
                }

            });
        }};
        server.start();

        int clients = 101;
        for (int i = 0; i < clients; i++) {
            int finalI = i;
            Thread.ofVirtual().start(() -> {
                SseStat stat = stats.get(finalI);
                if (stat == null) {
                    stat = new SseStat();
                    stats.put(finalI, stat);
                }
                stat.connecting = true;

                // change to HttpURLConnection from OkHTTP right here.
                try (okhttp3.Response response = httpGet("/sse", "sse_perf_client", ""+finalI)) {
                    stat.connected = true;

                    assertThat(response.code(), equalTo(200));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        int j = 0;
                        while (true) {
                            String line = reader.readLine();
                            stat.messagesReceived = stat.messagesReceived +1;
                            stat.messagesAsExpected = stat.messagesAsExpected + (line.equals("data: Message " + j++) ? 1 : 0);
                            assertThat(reader.readLine(), equalTo(""));
                        }
                    }
                } catch (IOException e) {
                    stat.clientIOE = stat.clientIOE +1;
                    //e.printStackTrace();
                }
            });
        }


        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            long serverConnectExceptions = stats.values().stream().filter(stat -> stat.serverConnectException).count();
            long connectingCount = stats.values().stream().filter(stat -> stat.connecting).count();
            long connectedCount = stats.values().stream().filter(stat -> stat.connected).count();
            int totalMessagesReceived = stats.values().stream().mapToInt(stat -> stat.messagesReceived).sum();
            int totalMessagesAsExpected = stats.values().stream().mapToInt(stat -> stat.messagesAsExpected).sum();
            int totalClientIOE = stats.values().stream().mapToInt(stat -> stat.clientIOE).sum();
            long totalServerIOE = stats.values().stream().filter(stat -> stat.serverIOE).count();

            System.out.printf("At %d secs, Stats entries: %d, Server Connect Exceptions: %d, Connecting: %d, Connected: %d, Messages Received: %d and as expected %d, Client IOExceptions: %d%n, Server IOExceptions: %d%n",
                    elapsed, stats.size(), serverConnectExceptions, connectingCount, connectedCount, totalMessagesReceived, totalMessagesAsExpected, totalClientIOE, totalServerIOE);

        }, 10, 10, TimeUnit.SECONDS);

    }
}
