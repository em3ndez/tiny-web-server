package tests;

import com.paulhammant.tnywb.TinyWeb;
import org.forgerock.cuppa.Test;
import org.mockito.Mockito;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class WithMockitoTests {
    TinyWeb.WebServer webServer;
    ExampleApp exampleApp;

    {
        describe("Given a mocked ExampleApp", () -> {
            describe("When accessing the Greeting GET endpoint", () -> {
                before(() -> {
                    exampleApp = Mockito.mock(ExampleApp.class);
                    webServer = new TinyWeb.WebServer(TinyWeb.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
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

}
