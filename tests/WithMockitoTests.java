package tests;

import org.forgerock.cuppa.Test;
import org.mockito.Mockito;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class WithMockitoTests {
    com.paulhammant.tnywb.Tiny.WebServer webServer;
    ExampleApp exampleApp;

    {
        describe("Given a mocked ExampleApp", () -> {
            describe("When accessing the Greeting GET endpoint", () -> {
                before(() -> {
                    exampleApp = Mockito.mock(ExampleApp.class);
                    webServer = new com.paulhammant.tnywb.Tiny.WebServer(com.paulhammant.tnywb.Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                        endPoint(com.paulhammant.tnywb.Tiny.Method.GET, "/greeting/(\\w+)/(\\w+)", exampleApp::foobar);
                    }};
                    webServer.start();
                    Mockito.doAnswer(invocation -> {
                        invocation.<com.paulhammant.tnywb.Tiny.Response>getArgument(1).write("invoked");
                        return null;
                    }).when(exampleApp).foobar(Mockito.any(com.paulhammant.tnywb.Tiny.Request.class), Mockito.any(com.paulhammant.tnywb.Tiny.Response.class), Mockito.<com.paulhammant.tnywb.Tiny.RequestContext>any());
                });

                it("Then it should invoke the ExampleApp foobar method", () -> {
                    bodyAndResponseCodeShouldBe(httpGet("/greeting/A/B"),
                            "invoked", 200);
                });

                after(() -> {
                    webServer.stop();
                    Mockito.verify(exampleApp).foobar(Mockito.any(com.paulhammant.tnywb.Tiny.Request.class),
                            Mockito.any(com.paulhammant.tnywb.Tiny.Response.class),
                            Mockito.<com.paulhammant.tnywb.Tiny.RequestContext>any());
                    webServer = null;
                });
            });
        });
    }

}
