package com.paulhammant.tinywebserver;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.model.TestBlock;
import org.forgerock.cuppa.reporters.DefaultReporter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;

import static com.paulhammant.tinywebserver.WebServer.exampleComposition;
import static okhttp3.Request.*;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Map;

@Test
public class WebServerTest {
    StringBuilder journal = new StringBuilder();
    WebServer.ExampleApp app = new WebServer.ExampleApp() {
        @Override
        public void foobar(WebServer.Request req, WebServer.Response res, Map<String, String> params) {
            journal.append("ExampleApp.foobar called with ").append(params.get("1"))
                    .append(" and ").append(params.get("2"));
            res.write("journaled");
        }
    };
    WebServer svr;

    {
        describe("For Example (Tiny) WebServer", () -> {
            describe("Echoing GET endpoint respond with..", () -> {
                before(() -> {
                    journal.delete(0, journal.length());
                    svr = exampleComposition(new String[0], app);
                    svr.start();
                });
                it("..Jimmy when Jimmy is a param ", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Jimmy")) {
                        assertThat(response.body().string(), equalTo("User profile: Jimmy"));
                    }
                });
                it("..Themla when Thelma is a param ", () -> {
                    try (Response response = httpGet("http://localhost:8080/users/Thelma")) {
                        assertThat(response.body().string(), equalTo("User profile: Thelma"));
                    }
                });
                after(() -> {
                    svr.stop();
                    assertThat(journal.toString(), equalTo(""));
                });
            });
            describe("Greeting GET endpoint ", () -> {
                before(() -> {
                    journal.delete(0, journal.length());
                    svr = exampleComposition(new String[0], app);
                    svr.start();
                });
                it("invokes ExampleApp method", () -> {
                    try (Response response = httpGet("http://localhost:8080/greeting/A/B")) {
                        assertThat(response.body().string(), equalTo("journaled"));
                    }
                });
                after(() -> {
                    svr.stop();
                    assertThat(journal.toString(), equalTo("ExampleApp.foobar called with A and B"));
                });
            });
        });
    }

    private static @NotNull Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url(url)
                .get().build()).execute();
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        TestBlock rootBlock = runner.defineTests(Collections.singletonList(WebServerTest.class));
        runner.run(rootBlock, new DefaultReporter());
    }
}
