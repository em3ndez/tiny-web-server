package tests;

import org.forgerock.cuppa.Test;

import static com.paulhammant.tnywb.Tiny.Method.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

@Test
public class PathRegistrationTests {

    private com.paulhammant.tnywb.Tiny.WebServer server;

    {
        describe("Given a TinyWeb server with a path registered", () -> {

            before(() -> {
                server = new com.paulhammant.tnywb.Tiny.WebServer(com.paulhammant.tnywb.Tiny.Config.create().withHostAndWebPort("localhost", 8080)) {{
                    path("/duplicate", () -> {
                        endPoint(GET, "/endpoint", (req, res, ctx) -> {
                            res.write("First registration");
                        });
                    });
                }};
            });

            it("Then it should not be able to register the same path again", () -> {
                try {
                    new com.paulhammant.tnywb.Tiny.ServerComposition(server) {{
                        // see above - is registered already
                        path("/duplicate", () -> {
                            endPoint(GET, "/endpoint", (req, res, ctx) -> {
                                res.write("Second registration");
                            });
                        });
                    }};
                    throw new AssertionError("should have throw IllegalStateException");
                } catch (IllegalStateException e) {
                    assertThat(e.getMessage(), equalTo("Path already registered: /duplicate"));
                }
            });
        });

        describe("Given a TinyWeb server with a path registered", () -> {

            before(() -> {
                server = new com.paulhammant.tnywb.Tiny.WebServer(com.paulhammant.tnywb.Tiny.Config.create().withWebPort(8080)) {{
                    path("/dupli", () -> {
                        path("/cate", () -> {
                            endPoint(GET, "/endpoint", (req, res, ctx) -> {
                                res.write("First registration");
                            });
                        });
                    });
                }};
            });

            it("Then it should not be able to register the same path again", () -> {
                try {
                    new com.paulhammant.tnywb.Tiny.ServerComposition(server) {{
                        // see above - is registered already
                        path("/dupli/cate", () -> {
                            endPoint(GET, "/endpoint", (req, res, ctx) -> {
                                res.write("Second registration");
                            });
                        });
                    }};
                    throw new AssertionError("should have throw IllegalStateException");
                } catch (IllegalStateException e) {
                    assertThat(e.getMessage(), equalTo("Path already registered: /dupli/cate"));
                }
            });
        });
    }
}
