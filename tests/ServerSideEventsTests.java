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

import com.paulhammant.tnywb.Tiny;
import org.forgerock.cuppa.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static tests.Suite.httpGet;

@Test
public class ServerSideEventsTests {
    com.paulhammant.tnywb.Tiny.WebServer webServer;

    {
        describe("Given a Tiny web server with an SSE endpoint", () -> {
            before(() -> {
                webServer = new com.paulhammant.tnywb.Tiny.WebServer(com.paulhammant.tnywb.Tiny.Config.create().withHostAndWebPort("localhost", 8080)) {{
                    path("/sse", () -> {
                        endPoint(Tiny.HttpMethods.GET, "/events", (req, res, ctx) -> {
                            res.setHeader("Content-Type", "text/event-stream");
                            res.setHeader("Cache-Control", "no-cache");
                            res.setHeader("Connection", "keep-alive");
                            try {
                                res.sendResponseHeaders(200, 0); // Send headers once
                                OutputStream outputStream = res.getResponseBody();
                                outputStream.write("data: Initial event\n\n".getBytes(StandardCharsets.UTF_8));
                                outputStream.flush();
                                // Simulate sending events over time
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(1000);
                                        outputStream.write("data: Event 1\n\n".getBytes(StandardCharsets.UTF_8));
                                        outputStream.flush();
                                        Thread.sleep(1000);
                                        outputStream.write("data: Event 2\n\n".getBytes(StandardCharsets.UTF_8));
                                        outputStream.flush();
                                    } catch (InterruptedException | IOException e) {
                                    }
                                }).start();
                            } catch (IOException e) {
                            }
                        });
                    });
                }};
                webServer.start();
            });

            it("Then it should receive server-sent events", () -> {
                try (okhttp3.Response response = httpGet("/sse/events")) {
                    assertThat(response.code(), equalTo(200));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        String line;
                        boolean initialEventReceived = false;
                        boolean event1Received = false;
                        boolean event2Received = false;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("data: Initial event")) {
                                initialEventReceived = true;
                            } else if (line.contains("data: Event 1")) {
                                event1Received = true;
                            } else if (line.contains("data: Event 2")) {
                                event2Received = true;
                            }
                            if (initialEventReceived && event1Received && event2Received) {
                                break;
                            }
                        }
                        assertThat(initialEventReceived, is(true));
                        assertThat(event1Received, is(true));
                        assertThat(event2Received, is(true));
                    }
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
