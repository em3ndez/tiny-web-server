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

import java.util.Map;
import java.util.regex.Pattern;

import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

@Test
public class RequestStatsTests {
    TinyWeb.Server webServer;
    StringBuilder statsStr = new StringBuilder();

    public static final String TEST_SLASH_ALL = "/.*";

    {
        describe("Given a TinyWeb server with filters and an endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(TinyWeb.Config.create().withHostAndWebPort("localhost", 8080)) {
                    @Override
                    protected void recordStatistics(String path, Map<String, Object> stats) {
                        String string = stats.toString()
                                .replaceAll("ration=\\d{2}", "ration=50")
                                .replaceAll("ration=\\d{3}", "ration=200")
                                .replaceAll("ration=\\d{1},", "ration=50,");
                        statsStr.append(string);
                    }

                    {
                        path("/test", () -> {
                            filter(GET, TEST_SLASH_ALL, (req, res, ctx) -> {
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

            it("Then it should collect statistics for filters and endpoint", () -> {
                okhttp3.Response response = httpGet("/test/abc");
                assertThat(response.code(), equalTo(200));
                assertThat(response.body().string(), equalTo("hello"));
                sleep(10);
                assertThat(statsStr.toString(), equalTo("{duration=200, path=/test/abc, endpoint=^/test/abc$, endpointDuration=50, " +
                        "filters=[FilterStat[path=^/test/.*$, result=ok, duration=50], " +
                                 "FilterStat[path=^/test/a.*$, result=ok, duration=50], " +
                                 "FilterStat[path=^/test/.*c$, result=ok, duration=50]], " +
                        "status=200}"));
                statsStr = new StringBuilder();
            });
            it("Then it should collect statistics for missing endpoints", () -> {
                okhttp3.Response response = httpGet("/blahhhhh");
                assertThat(response.code(), equalTo(404));
                assertThat(response.body().string(), equalTo("Not found"));
                sleep(10);
                assertThat(statsStr.toString(), equalTo("{duration=50, path=/blahhhhh, endpoint=unmatched, filters=[], status=404}"));
                statsStr = new StringBuilder();
            });

            it("Then it should not collect statistics filter that notionally match when the endPoint is a 404", () -> {
                okhttp3.Response response = httpGet("/test/zzz");
                assertThat(Pattern.compile("/test/.*").matcher("/test/zzz").matches(), equalTo(true));
                assertThat(response.code(), equalTo(404));
                assertThat(response.body().string(), equalTo("Not found"));
                sleep(10);
                assertThat(statsStr.toString(), equalTo("{duration=50, path=/test/zzz, endpoint=unmatched, filters=[], status=404}"));
                statsStr = new StringBuilder();
            });

            after(() -> {
                webServer.stop();
                webServer = null;

            });
        });
    }
}
