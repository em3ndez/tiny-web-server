package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.httpGet;

@Test
public class TinyWebMockitoTests {
    TinyWeb.Server webServer;
    TinyWebTests.ExampleApp exampleApp;

    {
        describe("Given a mocked ExampleApp", () -> {
            describe("When accessing the Greeting GET endpoint", () -> {
                before(() -> {
                    exampleApp = Mockito.mock(TinyWebTests.ExampleApp.class);
                    webServer = new TinyWeb.Server(8080, 8081) {{
                        endPoint(TinyWeb.Method.GET, "/greeting/(\\w+)/(\\w+)", exampleApp::foobar);
                    }};
                    webServer.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<TinyWeb.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(exampleApp).foobar(Mockito.any(TinyWeb.Request.class), Mockito.any(TinyWeb.Response.class), Mockito.<TinyWeb.RequestContext>any());
                });

                it("Then it should invoke the ExampleApp foobar method", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/greeting/A/B"),
                            "invoked", 200);
                });

                after(() -> {
                    webServer.stop();
                    Mockito.verify(exampleApp).foobar(Mockito.any(TinyWeb.Request.class),
                            Mockito.any(TinyWeb.Response.class),
                            Mockito.<TinyWeb.RequestContext>any());
                    webServer = null;
                });
            });
        });
    }

    private static void bodyAndResponseCodeShouldBe(okhttp3.Response response, String bodyShouldBe, int rcShouldBe) throws IOException {
        try (response) {
            assertThat(response.body().string(), equalTo(bodyShouldBe));
            assertThat(response.code(), equalTo(rcShouldBe));
        }
    }
}
