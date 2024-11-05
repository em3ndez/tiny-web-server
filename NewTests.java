import com.paulhammant.tnywb.TinyWeb;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.Test;
import org.forgerock.cuppa.reporters.DefaultReporter;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;

@Test
public class NewTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with ConcreteExtensionToServerComposition", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081);
                new ConcreteExtensionToServerComposition(webServer);
                webServer.start();
            });

            it("Then it should return 'Hello from /foo/bar' when accessing /foo/bar", () -> {
                try (okhttp3.Response response = httpGet("/foo/bar")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().string(), equalTo("Hello from /foo/bar"));
                }
            });

            it("Then it should return 403 Forbidden when accessing /foo/bar with 'X-Example-Header' missing", () -> {
                try (okhttp3.Response response = httpGet("/foo/bar")) {
                    assertThat(response.code(), equalTo(403));
                    assertThat(response.body().string(), equalTo("Forbidden"));
                }
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });

    }


    private okhttp3.Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Request.Builder()
                .url("http://localhost:8080" + url)
                .get().build()).execute();
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(NewTests.class)), new DefaultReporter());
    }
}
