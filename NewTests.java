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

    {
        describe("Given a TinyWeb server with a chunked response endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    endPoint(TinyWeb.Method.GET, "/chunked", (req, res, ctx) -> {
                        Random random = new Random();
                        BigDecimal totalSum = BigDecimal.ZERO;
                        OutputStream out = res.getResponseBody();
                        res.setHeader("Transfer-Encoding", "chunked");

                        try {
                            res.sendResponseHeaders(200, 0);

                            for (int i = 0; i < 100; i++) {
                                int[] numbers = new int[256 * 1024]; // 1MB of integers
                                for (int j = 0; j < numbers.length; j++) {
                                    numbers[j] = random.nextInt();
                                    totalSum = totalSum.add(BigDecimal.valueOf(numbers[j]));
                                }
                                ByteBuffer buffer = ByteBuffer.allocate(numbers.length * Integer.BYTES);
                                for (int number : numbers) {
                                    buffer.putInt(number);
                                }
                                try {
                                    writeChunk(out, buffer.array());
                                } catch (IOException e) {
                                    throw new RuntimeException("IOE during chunk testing 1", e);
                                }
                            }

                            // Send the total sum as the last chunk
                            writeChunk(out, ("SUM:" + totalSum.toString()).getBytes(StandardCharsets.UTF_8));
                            writeChunk(out, new byte[0]); // End of chunks
                            out.close();
                        } catch (IOException e) {
                            throw new RuntimeException("IOE during chunk testing 2", e);
                        }
                    });
                }};
                webServer.start();
            });

            it("Then it should return the response in chunks", () -> {
                try (okhttp3.Response response = httpGet("/chunked")) {
                    assertThat(response.code(), equalTo(200));
                    String responseBody = response.body().string();
                    // Remove chunk size headers and verify the content
                    String[] parts = responseBody.split("SUM:");
                    String dataPart = parts[0];
                    String sumPart = parts[1].trim();

                    BigDecimal calculatedSum = BigDecimal.ZERO;
                    ByteBuffer buffer = ByteBuffer.wrap(dataPart.getBytes(StandardCharsets.ISO_8859_1));
                    while (buffer.hasRemaining()) {
                        calculatedSum = calculatedSum.add(BigDecimal.valueOf(buffer.getInt()));
                    }

                    assertThat(calculatedSum.toString(), equalTo(sumPart));
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }

    private void writeChunk(OutputStream out, byte[] chunk) throws IOException {
        String chunkSize = Integer.toHexString(chunk.length) + "\r\n";
        out.write(chunkSize.getBytes(StandardCharsets.US_ASCII));
        out.write(chunk);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
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
