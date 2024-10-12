package com.paulhammant.tinywebserver;

import com.paulhammant.tinywebserver.TinyWeb.Server;
import com.paulhammant.tinywebserver.TinyWeb.SimulatedResponse;
import org.forgerock.cuppa.Cuppa.*;
import java.util.Collections;
import java.nio.file.Paths;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import org.forgerock.cuppa.Test;
import org.hamcrest.MatcherAssert;

import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;

@Test
public class FooTest {



}
    {
        describe("Static file serving tests", () -> {
            Server server;

            before(() -> {
                server = new Server(8080) {{
                    serveStaticFiles("/static", Paths.get("src/test/resources/static").toString());
                }}.start();
            });

            it("should serve a static file correctly", () -> {
                SimulatedResponse response = server.directRequest(
                        TinyWeb.Method.GET,
                        "/static/test.txt",
                        null,
                        Collections.emptyMap()
                );
                assertThat(response.statusCode(), equalTo(200));
                assertThat(response.body(), containsString("This is a test file."));
            });

            it("should return 404 for a non-existent static file", () -> {
                SimulatedResponse response = server.directRequest(
                        TinyWeb.Method.GET,
                        "/static/nonexistent.txt",
                        null,
                        Collections.emptyMap()
                );
                assertThat(response.statusCode(), equalTo(404));
                assertThat(response.body(), containsString("File not found"));
            });

            it("should prevent directory traversal attack", () -> {
                SimulatedResponse response = server.directRequest(
                        TinyWeb.Method.GET,
                        "/static/../TinyWeb.java",
                        null,
                        Collections.emptyMap()
                );
                assertThat(response.statusCode(), equalTo(404));
                assertThat(response.body(), containsString("File not found"));
            });

            after(() -> {
                server.stop();
            });
        });
    }
