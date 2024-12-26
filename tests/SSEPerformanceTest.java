package tests;

import com.paulhammant.tiny.Tiny;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

public class SSEPerformanceTest {

    public static void main(String[] args) {


        long startTime = System.currentTimeMillis();
        AtomicInteger connectExceptions = new AtomicInteger(0);
        AtomicInteger otherIOExceptions = new AtomicInteger(0);

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
//                        Thread.sleep(new java.util.Random().nextInt(2001));
                    }
//                } catch (InterruptedException e) {
                } catch (ConnectException e) {
                    connectExceptions.incrementAndGet();
                } catch (IOException e) {
                    otherIOExceptions.incrementAndGet();
                }

            });
        }};
        server.start();

        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);
        AtomicInteger messagesReceived = new AtomicInteger(0);

        int clients = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(clients);
        for (int i = 0; i < clients; i++) {
            executor.submit(() -> {
                try (okhttp3.Response response = httpGet("/sse")) {
                    successfulConnections.incrementAndGet();
                    assertThat(response.code(), equalTo(200));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        for (int j = 0; j < 10; j++) {
                            assertThat(reader.readLine(), equalTo("data: Message " + j));
                            messagesReceived.incrementAndGet();
                            assertThat(reader.readLine(), equalTo(""));
                        }
                    }
                } catch (IOException e) {
                    failedConnections.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }


        ScheduledExecutorService statsScheduler = Executors.newScheduledThreadPool(1);
        statsScheduler.scheduleAtFixedRate(() -> {
            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            System.out.println(elapsedTime + " secs: Server connect exceptions: " + connectExceptions.get());
            System.out.println("Server other IO exceptions: " + otherIOExceptions.get());

            System.out.println("Successful client connections: " + successfulConnections.get());
            System.out.println("Failed client connections: " + failedConnections.get());
            System.out.println("Client Messages received: " + messagesReceived.get());
        }, 0, 10, TimeUnit.SECONDS);

        // Ensure the scheduler is properly shut down
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            statsScheduler.shutdown();
            server.stop();
            try {
                if (!statsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    statsScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                statsScheduler.shutdownNow();
            }
        }));

    }
}
