package tests;

import com.paulhammant.tiny.Tiny;
import org.forgerock.cuppa.Test;
import org.mockito.Mockito;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class WithMockitoTests {
    Tiny.WebServer webServer;
    ExampleApp exampleApp;

    {
        describe("Given a mocked ExampleApp", () -> {
            describe("When accessing the Greeting GET endpoint", () -> {
                before(() -> {
                    exampleApp = Mockito.mock(ExampleApp.class);
                    webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                        endPoint(Tiny.HttpMethods.GET, "/greeting/(\\w+)/(\\w+)", exampleApp::foobar);
                    }};
                    webServer.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<Tiny.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(exampleApp).foobar(Mockito.any(Tiny.Request.class), Mockito.any(Tiny.Response.class), Mockito.<Tiny.RequestContext>any());
                });

                it("Then it should invoke the ExampleApp foobar method", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/greeting/A/B"),
                            "invoked", 200);
                });

                after(() -> {
                    webServer.stop();
                    Mockito.verify(exampleApp).foobar(Mockito.any(Tiny.Request.class),
                            Mockito.any(Tiny.Response.class),
                            Mockito.<Tiny.RequestContext>any());
                    webServer = null;
                });
            });
        });
    }

}
