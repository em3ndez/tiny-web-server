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
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;
import static tests.WebSocketBroadcastDemo.sleepMillis;

public class SSEPerformanceTest {

    public static class SseStat {

        public boolean connectExcpetion;
        public boolean serverIOE;
        public boolean connected2;
        public boolean connected;
        public int messagesReceived;
        public int clientIOE;
    }

    public static void main(String[] args) {

        Map<Integer, SseStat> stats = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();

        Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080)) {
            @Override
            protected HttpServer makeHttpServer() throws IOException {
                return super.makeHttpServer();
            }

            @Override
            protected void newHttpExchange(HttpExchange exchange) {
            }

            @Override
            protected void exceptionDuringHandling(Throwable e, HttpExchange exchange) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
            @Override
            protected void serverException(Tiny.ServerException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }

            {
            endPoint(Tiny.HttpMethods.GET, "/sse", (req, res, ctx) -> {
                res.setHeader("Content-Type", "text/event-stream");
                res.setHeader("Cache-Control", "no-cache");
                res.setHeader("Connection", "keep-alive");
                String client = req.getHeaders().get("sse_perf_client").getFirst();
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
                    stat.connectExcpetion = true;
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

        int clients = 41;
        for (int i = 0; i < clients; i++) {
            int finalI = i;
            Thread.ofVirtual().start(() -> {
                SseStat stat = stats.get(finalI);
                if (stat == null) {
                    stat = new SseStat();
                    stats.put(finalI, stat);
                }
                stat.connected = true;

                try (okhttp3.Response response = httpGet("/sse")) {
                    stat.connected2 = true;

                    assertThat(response.code(), equalTo(200));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        int j = 0;
                        while (true) {
                            assertThat(reader.readLine(), equalTo("data: Message " + j++));
                            stat.messagesReceived = stat.messagesReceived +1;
                            assertThat(reader.readLine(), equalTo(""));
                        }
                    }
                } catch (IOException e) {
                    stat.clientIOE = stat.clientIOE +1;
                    e.printStackTrace();
                }
            });
        }

        sleepMillis(30000);

//        System.out.println("Took " + (System.currentTimeMillis() - startTime) + "ms");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            stream of stats here -
                    1. how many rows
                    2. how many connectExcpetion == true
                    3. how many connected == true
                    4. how many connected2 == true
                    5. total of messagesReceived
                    5. total of clientIOE


            float rate = ((float) successfulConnections.get() * elapsed / messagesReceived.get()) * 100;
            System.out.printf("At %d secs, THOSE NUMBERS ABOVE", elapsed, ,,,,);
        }, 10, 10, TimeUnit.SECONDS);

    }
}
