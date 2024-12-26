package tests;

import com.paulhammant.tiny.Tiny;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

public class SSEPerformanceTest {

    public static void main(String[] args) {
        Tiny.WebServer server = new Tiny.WebServer(Tiny.Config.create().withWebPort(8080)) {{
            endPoint(Tiny.HttpMethods.GET, "/sse", (req, res, ctx) -> {
                res.setHeader("Content-Type", "text/event-stream");
                res.setHeader("Cache-Control", "no-cache");
                res.setHeader("Connection", "keep-alive");
                try {
                    res.sendResponseHeaders(200, 0);
                    OutputStream outputStream = res.getResponseBody();
                    for (int i = 0; i < 10; i++) {
                        outputStream.write(("data: Message " + i + "\n\n").getBytes());
                        outputStream.flush();
                        Thread.sleep(new java.util.Random().nextInt(2001));
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }};
        server.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try (okhttp3.Response response = httpGet("/sse")) {
                    successfulConnections.incrementAndGet();
                    assertThat(response.code(), equalTo(200));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        for (int j = 0; j < 10; j++) {
                            assertThat(reader.readLine(), equalTo("data: Message " + j));
                            assertThat(reader.readLine(), equalTo(""));
                        }
                    }
                } catch (IOException e) {
                    failedConnections.incrementAndGet();
                    e.printStackTrace();
                }

            });
        }

        System.out.println("Successful connections: " + successfulConnections.get());
        System.out.println("Failed connections: " + failedConnections.get());

        server.stop();
    }
}
