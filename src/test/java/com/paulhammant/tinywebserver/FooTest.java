package com.paulhammant.tinywebserver;

import org.forgerock.cuppa.Cuppa.*;
import org.forgerock.cuppa.Test;
import org.hamcrest.MatcherAssert;

import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;

@Test
public class FooTest {

    TinyWeb.Server svr;

    {
        describe("Direct Request Handling", () -> {
            before(() -> {
                svr = TinyWeb.ExampleApp.exampleComposition(new String[0], new TinyWeb.ExampleApp());
                svr.start();
            });

            it("should return user profile for Jimmy via direct request", () -> {
                TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.GET,
                        "/users/Jimmy",
                        null,
                        Collections.emptyMap()
                );
                MatcherAssert.assertThat(response.body(), equalTo("User profile: Jimmy"));
                MatcherAssert.assertThat(response.statusCode(), equalTo(200));
                MatcherAssert.assertThat(response.contentType(), equalTo("text/plain"));
            });

            it("should return 404 for non-existent paths", () -> {
                TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.GET,
                        "/nonexistent",
                        null,
                        Collections.emptyMap()
                );
                MatcherAssert.assertThat(response.body(), equalTo("Not found"));
                MatcherAssert.assertThat(response.statusCode(), equalTo(404));
                MatcherAssert.assertThat(response.contentType(), equalTo("text/plain"));
            });

            it("should handle POST requests correctly", () -> {
                TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.POST,
                        "/echo",
                        "test post body",
                        Collections.emptyMap()
                );
                MatcherAssert.assertThat(response.body(), equalTo("You sent: test post body"));
                MatcherAssert.assertThat(response.statusCode(), equalTo(201));
            });

            it("should handle PUT requests correctly", () -> {
                TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.PUT,
                        "/update",
                        "test put body",
                        Collections.emptyMap()
                );
                MatcherAssert.assertThat(response.body(), equalTo("Updated data: test put body"));
                MatcherAssert.assertThat(response.statusCode(), equalTo(200));
            });

            it("should return 405 for unsupported DELETE method", () -> {
                TinyWeb.SimulatedResponse response = svr.directRequest(
                        TinyWeb.Method.DELETE,
                        "/users/Jimmy",
                        null,
                        Collections.emptyMap()
                );
                MatcherAssert.assertThat(response.body(), equalTo("Method not allowed"));
                MatcherAssert.assertThat(response.statusCode(), equalTo(405));
            });

            after(() -> {
                svr.stop();
            });
        });
    }
}
