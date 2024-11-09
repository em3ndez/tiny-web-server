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

import org.forgerock.cuppa.Test;

import static java.lang.Thread.sleep;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


import com.paulhammant.tnywb.TinyWeb;
import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.FilterResult.STOP;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static com.paulhammant.tnywb.TinyWeb.Method.POST;
import static com.paulhammant.tnywb.TinyWeb.Method.PUT;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class TinyWebTests {
    TinyWeb.Server webServer;

    {

        describe("Given an inlined Cuppa application", () -> {
            describe("When the endpoint can extract parameters", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            path("/v1", () -> {
                                endPoint(GET, "/test/(\\w+)", (req, res, ctx) -> {
                                    res.write("Parameter: " + ctx.getParam("1"));
                                });
                            });
                        });
                    }}.start();
                });
                it("Then it should extract parameters correctly from the path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/test/123"), "Parameter: 123", 200);
                });
                it("Then it should return 404 when two parameters are provided for a one-parameter path", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/v1/test/123/456"), "Not found", 404);
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When the endpoint can extract query parameters", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        path("/api2", () -> {
                            endPoint(GET, "/test/(\\w+)", (req, res, ctx) -> {
                                res.write("Parameter: " + ctx.getParam("1") + " " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });
                it("Then it should handle query parameters correctly", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api2/test/123?a=1&b=2"), "Parameter: 123 {a=1, b=2}", 200);
                });
                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When an application exception is thrown from an endpoint", () -> {
                final StringBuilder appHandlingExceptions = new StringBuilder();
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                            path("/api", () -> {
                                endPoint(GET, "/error", (req, res, ctx) -> {
                                    throw new RuntimeException("Deliberate exception");
                                });


                            });
                        }
                        @Override
                        protected void exceptionDuringHandling(Exception e) {
                            appHandlingExceptions.append("appHandlingException exception: " + e.getMessage());
                        }
                    }.start();
                });

                it("Then it should return 500 and an error message for a runtime exception", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/error"),
                        "Server error", 500);
                    assertThat(appHandlingExceptions.toString(),
                            equalTo("appHandlingException exception: Deliberate exception"));
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });
            describe("When the endpoint has query-string parameters", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            endPoint(GET, "/query", (req, res, ctx) -> {
                                res.write("Query Params: " + req.getQueryParams());
                            });
                        });
                    }}.start();
                });

                it("Then it should parse query parameters correctly", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/query?name=John&age=30"),
                            "Query Params: {name=John, age=30}", 200);
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When response headers are sent to the client", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, -1) {{
                        path("/api", () -> {
                            endPoint(GET, "/header-test", (req, res, ctx) -> {
                                res.setHeader("X-Custom-Header", "HeaderValue");
                                res.write("Header set");
                            });
                        });
                    }}.start();
                });

                it("Then it should set the custom header correctly", () -> {
                    try (okhttp3.Response response = httpGet("/api/header-test")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.header("X-Custom-Header"), equalTo("HeaderValue"));
                        assertThat(response.body().string(), equalTo("Header set"));
                    }
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When an exception is thrown from a filter", () -> {
                final StringBuilder appHandlingExceptions = new StringBuilder();
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        path("/api", () -> {
                            filter(GET, "/error", (req, res, ctx) -> {
                                throw new RuntimeException("Deliberate exception in filter");
                            });
                            endPoint(GET, "/error", (req, res, ctx) -> {
                                res.write("This should not be reached");

                            });
                        });
                    }

                        @Override
                        protected void exceptionDuringHandling(Exception e) {
                            appHandlingExceptions.append("appHandlingException exception: " + e.getMessage());
                        }
                    }.start();
                });

                it("Then it should return 500 and an error message for a runtime exception in a filter", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/api/error"),
                                "Server Error", 500);
                    assertThat(appHandlingExceptions.toString(),
                            equalTo("appHandlingException exception: Deliberate exception in filter"));
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });

            describe("When testing static file serving", () -> {
                before(() -> {
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        serveStaticFilesAsync("/static", ".");
                    }}.start();
                });

                it("Then it should serve a static file correctly", () -> {
                    try (okhttp3.Response response = httpGet("/static/README.md")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(), containsString("Cuppa-Framework"));
                    }
                });

                it("Then it should return 404 for a non-existent static file", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/static/nonexistent.txt"),
                            "Not found", 404);
                });

                it("Then it should prevent directory traversal attacks", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/static/../../anything.java"),
                            "Not found", 404); //TODO 404?
                });

                after(() -> {
                    webServer.stop();
                    webServer = null;
                });
            });


        });
    }

    public static class IsEncryptedByUs {
        public static Authentication decrypt(String allegedlyLoggedInCookie) {
            String rot47ed = rot47(allegedlyLoggedInCookie);
            // check is an email address
            if (rot47ed.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$")) {
                return new Authentication(true, rot47ed);
            } else {
                return new Authentication(false, null);
            }
        }
    }
    public record Authentication(boolean authentic, String user) {}

    private static String rot47(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= '!' && c <= 'O') {
                result.append((char) (c + 47));
            } else if (c >= 'P' && c <= '~') {
                result.append((char) (c - 47));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}

