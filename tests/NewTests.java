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
import java.util.Map;
import java.util.Random;

import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

@Test
public class NewTests {
    TinyWeb.Server webServer;
    StringBuilder statsStr = new StringBuilder();

    {
        describe("Given a TinyWeb server with filters and an endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {
                    @Override
                    protected void recordStatistics(String path, Map<String, Object> stats) {
                        String string = stats.toString()
                                .replaceAll("=\\d{2}", "=50")
                                .replaceAll("=\\d{3}", "=200");
                        statsStr.append(string);
                    }

                    {
                        path("/test", () -> {
                            filter(GET, "/.*", (req, res, ctx) -> {
                                try {
                                    sleep(50);
                                } catch (InterruptedException e) {
                                }
                                return CONTINUE;
                            });
                            filter(GET, "/a.*", (req, res, ctx) -> {
                                try {
                                    sleep(50);
                                } catch (InterruptedException e) {
                                }
                                return CONTINUE;
                            });
                            filter(GET, "/.*c", (req, res, ctx) -> {
                                try {
                                    sleep(50);
                                } catch (InterruptedException e) {
                                }
                                return CONTINUE;
                            });
                            endPoint(GET, "/abc", (req, res, ctx) -> {
                                try {
                                    sleep(50);
                                } catch (InterruptedException e) {
                                }
                                res.write("hello");
                            });
                    });
                }};
                webServer.start();
            });

            only().it("Then it should collect statistics for filters and endpoint", () -> {
                okhttp3.Response response = httpGet("/test/abc");
                assertThat(response.code(), equalTo(200));
                assertThat(response.body().string(), equalTo("hello"));
                sleep(10);
                assertThat(statsStr.toString(), equalTo("{duration=200, endpoint=^/test/abc$, endpointDuration=50, " +
                        "filters=[FilterStat[path=^/test/.*$, result=ok, duration=50], " +
                                 "FilterStat[path=^/test/a.*$, result=ok, duration=50], " +
                                 "FilterStat[path=^/test/.*c$, result=ok, duration=50]], " +
                        "status=200}"));
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
