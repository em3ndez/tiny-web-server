/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) Paul Hammant, 2024
 */

package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

@Test
public class ChunkedTests {
    TinyWeb.WebServer webServer;

    {
        describe("Given a TinyWeb server with a chunked response endpoint", () -> {
            BigDecimal[] totalSum = { BigDecimal.ZERO };
            before(() -> {
                webServer = new TinyWeb.WebServer(TinyWeb.Config.create().withHostAndWebPort("localhost", 8080)) {{
                    endPoint(TinyWeb.Method.GET, "/chunked", (req, res, ctx) -> {
                        Random random = new Random();
                        OutputStream out = res.getResponseBody();
                        res.setHeader("Transfer-Encoding", "chunked");

                        try {
                            res.sendResponseHeaders(200, 0);
                            for (int i = 0; i < 10; i++) {
                                int number = random.nextInt();
                                totalSum[0] = totalSum[0].add(BigDecimal.valueOf(number));
                                String numberString = Integer.toString(number);
                                res.writeChunk(out, numberString.getBytes(StandardCharsets.UTF_8));
                            }

                            res.writeChunk(out, new byte[0]); // End of chunks
                            out.close();
                        } catch (IOException e) {
                            throw new AssertionError("IOE during chunk testing 2", e);
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
                    // OkHttp reads all the chunks into one for you
                    // Split the response back into chunks
                    String[] parts = responseBody.split("\r\n|\n");
                    BigDecimal calculatedSum = BigDecimal.ZERO;
                    String sumPart = "";

                    int sz = 0;
                    for (String part : parts) {
                        if (part.matches("^[0-9a-fA-F]$")) {
                            sz = Integer.valueOf(part, 16);
                            //System.out.println("SZ " + sz);
                            // Skip chunk size lines
                            continue;
                        } else if (!part.isEmpty()) {
                            try {
                                // Ensure the part is a valid integer
                                String num = part.trim();
                                //ystem.out.println("num " + num);
                                assertThat(num.length(), equalTo(sz));
                                calculatedSum = calculatedSum.add(BigDecimal.valueOf(Integer.parseInt(num)));
                            } catch (NumberFormatException e) {
                                throw new AssertionError(e.getMessage(), e);
                            }
                        }
                    }

                    assertThat(calculatedSum, equalTo(totalSum[0]));
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
