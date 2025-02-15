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

import com.paulhammant.tiny.Tiny;
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
import static tests.WebSocketBroadcastDemo.sleepMillis;

@Test
public class ServerSideEventsTests {
    Tiny.WebServer webServer;

    {
        describe("Given a Tiny web server with an SSE endpoint", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080)) {{
                    path("/sse", () -> {
                        endPoint(Tiny.HttpMethods.GET, "/events", (req, res, ctx) -> {
                            res.setHeader("Content-Type", "text/event-stream");
                            res.setHeader("Cache-Control", "no-cache");
                            res.setHeader("Connection", "keep-alive");
                            try {
                                res.sendResponseHeaders(200, 0); // Send headers once
                                OutputStream outputStream = res.getResponseBody();
                                outputStream.write("data: Initial event\n\n".getBytes(StandardCharsets.UTF_8));
                                try {
                                    outputStream.flush();
                                } catch (IOException e) {
                                    System.err.println("Client disconnected: " + e.getMessage());
                                    return; // Exit the loop if the client disconnects
                                }
                                // Simulate sending events over time
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(new java.util.Random().nextInt(2001));
                                        outputStream.write("data: Event 1\n\n".getBytes(StandardCharsets.UTF_8));
                                        outputStream.flush();
                                        Thread.sleep(new java.util.Random().nextInt(2001));
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

            it("Then a client should receive server-sent events", () -> {
                try (okhttp3.Response response = httpGet("/sse/events")) {
                    assertThat(response.code(), equalTo(200));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        assertThat(reader.readLine(), equalTo("data: Initial event"));
                        assertThat(reader.readLine(), equalTo(""));
                        assertThat(reader.readLine(), equalTo("data: Event 1"));
                        assertThat(reader.readLine(), equalTo(""));
                        assertThat(reader.readLine(), equalTo("data: Event 2"));
                        assertThat(reader.readLine(), equalTo(""));
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
