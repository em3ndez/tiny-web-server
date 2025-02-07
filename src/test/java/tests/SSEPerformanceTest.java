package tests;

import com.paulhammant.tiny.Tiny;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;

import static com.paulhammant.tiny.Tiny.FilterAction.CONTINUE;
import static com.paulhammant.tiny.Tiny.HttpMethods.ALL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class SSEPerformanceTest {

    public static class SseStat {

        public boolean serverConnectException;
        public boolean serverIOE;
        public boolean connected;
        public boolean connecting;
        public int messagesReceived;
        public int messagesAsExpected;
        public String clientIOEs = "";
        public boolean bindException;
    }

    public static void main(String[] args) {

        Map<Integer, SseStat> stats = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();

        Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebBacklog(30000)) {
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
    //                System.out.println("sse_perf_client " + client);
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

        int clients = 10000;
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
                try {
                    URL url = new URL("http://localhost:8080/sse");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("sse_perf_client", "" + finalI);
                    connection.setRequestProperty("Accept", "text/event-stream");

                    assertThat(connection.getResponseCode(), equalTo(200));

                    stat.connected = true;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        int j = 0;
                        while (true) {
                            String line = reader.readLine();
                            stat.messagesReceived = stat.messagesReceived + 1;
                            stat.messagesAsExpected = stat.messagesAsExpected + (line.equals("data: Message " + j++) ? 1 : 0);
                            assertThat(reader.readLine(), equalTo(""));
                        }
                    }
                } catch (IOException e) {
                    if (e instanceof BindException) {
                        stat.bindException = true;
                    } else if (!stat.clientIOEs.contains(e.getMessage())) {
                        stat.clientIOEs += e.getClass().getName() + "-" + e.getMessage() + ",";
                    }
                }
            });
        }


        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            long serverConnectExceptions = stats.values().stream().filter(stat -> stat.serverConnectException).count();
            long connectingCount = stats.values().stream().filter(stat -> stat.connecting).count();
            long connectedCount = stats.values().stream().filter(stat -> stat.connected).count();
            long bindExceptions = stats.values().stream().filter(stat -> stat.bindException).count();
            int totalMessagesReceived = stats.values().stream().mapToInt(stat -> stat.messagesReceived).sum();
            int totalMessagesAsExpected = stats.values().stream().mapToInt(stat -> stat.messagesAsExpected).sum();
            Map<String, Integer> exceptionSummary = new ConcurrentHashMap<>();
            stats.values().forEach(stat -> {
                String[] exceptions = stat.clientIOEs.split(",");
                for (String exception : exceptions) {
                    exceptionSummary.merge(exception.trim(), 1, Integer::sum);
                }
            });

            StringBuilder exceptionSummaryString = new StringBuilder();
            exceptionSummary.forEach((exception, count) -> {
                if (!exception.isEmpty()) {
                    exceptionSummaryString.append(exception).append(": ").append(count).append(", ");
                }
            });

            String exceptionSummaryOutput = exceptionSummaryString.length() > 0
                    ? exceptionSummaryString.substring(0, exceptionSummaryString.length() - 2)
                    : "No exceptions";
            long totalServerIOE = stats.values().stream().filter(stat -> stat.serverIOE).count();

            System.out.printf("At %d secs, Stats entries: %d, Server Connect Exceptions: %d, Connecting: %d, Connected: %d, Messages Received: %d and as expected %d, Client BindExceptions: %d, Server IOExceptions: %d%n",
                    elapsed, stats.size(), serverConnectExceptions, connectingCount, connectedCount, totalMessagesReceived, totalMessagesAsExpected, bindExceptions, totalServerIOE);

            System.out.println("Range of client exceptions, other than BindException: " + exceptionSummaryOutput);

        }, 10, 10, TimeUnit.SECONDS);

    }
}
