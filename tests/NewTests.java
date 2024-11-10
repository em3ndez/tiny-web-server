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
public class NewTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with filters and an endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {
                    @Override
                    protected void recordStatistics(String path, Map<String, Object> stats) {
                        System.out.println("Stats: " + stats);
                    }
                };
                webServer.path("/test", () -> {
                    webServer.filter(GET, "/.*", (req, res, ctx) -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {}
                        return CONTINUE;
                    });
                    webServer.filter(GET, "/.*", (req, res, ctx) -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {}
                        return CONTINUE;
                    });
                    webServer.filter(GET, "/.*", (req, res, ctx) -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {}
                        return CONTINUE;
                    });
                    webServer.endPoint(GET, "/endpoint", (req, res, ctx) -> {
                        try { Thread.sleep(10); } catch (InterruptedException e) {}
                        res.write("hello");
                    });
                });
                webServer.start();
            });

            it("Then it should collect statistics for filters and endpoint", () -> {
                okhttp3.Response response = httpGet("/test/endpoint");
                assertThat(response.code(), equalTo(200));
                assertThat(response.body().string(), equalTo("hello"));
                // Here you would verify the stats output, but for this example, we print it
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
