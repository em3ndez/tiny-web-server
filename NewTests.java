import com.paulhammant.tnywb.TinyWeb;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.forgerock.cuppa.Test;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

@Test
public class NewTests {
    TinyWeb.Server webServer;

    {
        describe("Given a TinyWeb server with a chunked response endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    endPoint(TinyWeb.Method.GET, "/chunked", (req, res, ctx) -> {
                        res.sendResponseChunked("This is a chunked response.", 200);
                    });
                }};
                webServer.start();
            });

            it("Then it should return the response in chunks", () -> {
                try (okhttp3.Response response = httpGet("/chunked")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().string(), equalTo("This is a chunked response."));
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
}
