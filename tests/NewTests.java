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

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static tests.Suite.httpGet;

@Test
public class NewTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with an SSE endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/sse", () -> {
                        endPoint(TinyWeb.Method.GET, "/events", (req, res, ctx) -> {
                            res.setHeader("Content-Type", "text/event-stream");
                            res.setHeader("Cache-Control", "no-cache");
                            res.setHeader("Connection", "keep-alive");
                            res.write("data: Initial event\n\n");
                            // Simulate sending events over time
                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000);
                                    res.write("data: Event 1\n\n");
                                    Thread.sleep(1000);
                                    res.write("data: Event 2\n\n");
                                } catch (InterruptedException e) {
                                }
                            }).start();
                        });
                    });
                }};
                webServer.start();
            });

            only().it("Then it should receive server-sent events", () -> {
                try (okhttp3.Response response = httpGet("/sse/events")) {
                    assertThat(response.code(), equalTo(200));
                    String body = response.body().string();
                    assertThat(body, containsString("data: Initial event"));
                    assertThat(body, containsString("data: Event 1"));
                    assertThat(body, containsString("data: Event 2"));
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
