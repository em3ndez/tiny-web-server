package tests;

import com.paulhammant.tiny.Tiny;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SSEPerformanceTest {

    public static void main(String[] args) {
        Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080)) {{
            endPoint(Tiny.HttpMethods.GET, "/sse", (req, res, ctx) -> {
                res.setHeader("Content-Type", "text/event-stream");
                res.setHeader("Cache-Control", "no-cache");
                res.setHeader("Connection", "keep-alive");
                try {
                    res.sendResponseHeaders(200, 0);
                    OutputStream outputStream = res.getResponseBody();
                    for (int i = 0; i < 10; i++) {
                        outputStream.write(("data: Message " + i + "\n\n").getBytes());
                        //outputStream.flush();
                        Thread.sleep(1000);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }};
        server.start();

        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 10000; i++) {
            executor.submit(() -> {
                try {
                    URL url = new URL("http://localhost:8080/sse");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setReadTimeout(10000);
                    connection.setConnectTimeout(10000);

                    if (connection.getResponseCode() == 200) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                System.out.println(line);
                            }
                        }
                        successfulConnections.incrementAndGet();
                    } else {
                        failedConnections.incrementAndGet();
                    }
                } catch (IOException e) {
                    failedConnections.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Successful connections: " + successfulConnections.get());
        System.out.println("Failed connections: " + failedConnections.get());

        server.stop();
    }
}
