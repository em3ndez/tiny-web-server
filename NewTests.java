import com.paulhammant.tnywb.TinyWeb;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;

@Test
public class NewTests {
    TinyWeb.Server webServer;
    BigDecimal totalSum = BigDecimal.ZERO;

    {
        describe("Given a TinyWeb server with a chunked response endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    endPoint(TinyWeb.Method.GET, "/chunked", (req, res, ctx) -> {
                        Random random = new Random();
                        OutputStream out = res.getResponseBody();
                        res.setHeader("Transfer-Encoding", "chunked");

                        try {
                            res.sendResponseHeaders(200, 0);
                            for (int i = 0; i < 10; i++) {
                                int number = random.nextInt();
                                totalSum = totalSum.add(BigDecimal.valueOf(number));
                                String numberString = Integer.toString(number);
                                res.writeChunk(out, numberString.getBytes(StandardCharsets.UTF_8));
                            }

                            res.writeChunk(out, new byte[0]); // End of chunks
                            out.close();
                        } catch (IOException e) {
                            throw new RuntimeException("IOE during chunk testing 2", e);
                        }
                    });
                }};
                webServer.start();
            });

            it("Then it should return the response in chunks", () -> {
                int i = 0;
                try (okhttp3.Response response = httpGet("/chunked")) {
                    assertThat(response.code(), equalTo(200));
                    String responseBody = response.body().string();
                    // Split the response into chunks
                    String[] parts = responseBody.split("\r\n|\n");
                    BigDecimal calculatedSum = BigDecimal.ZERO;
                    String sumPart = "";

                    for (String part : parts) {
                        if (part.matches("^[0-9a-fA-F]$")) {
                            // Skip chunk size lines
                            continue;
                        } else if (!part.isEmpty()) {
                            try {
                                // Ensure the part is a valid integer
                                calculatedSum = calculatedSum.add(BigDecimal.valueOf(Integer.parseInt(part.trim())));
                            } catch (NumberFormatException e) {
                                System.out.println("Client: Skipping non-integer chunk " + part);
                            }
                        }
                    }

                    assertThat(calculatedSum, equalTo(totalSum));
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }


    private okhttp3.Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Request.Builder()
                .url("http://localhost:8080" + url)
                .get().build()).execute();
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(NewTests.class)), new DefaultReporter());
    }
}
