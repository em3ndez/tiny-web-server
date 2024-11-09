package tests;

import static okhttp3.Request.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import okhttp3.OkHttpClient;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.reporters.DefaultReporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

public class Suite {

    public static @NotNull okhttp3.Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url("http://localhost:8080" + url)
                .get().build()).execute();
    }

    public static @NotNull okhttp3.Response httpGet(String url, String hdrKey, String hdrVal) throws IOException {
        return new OkHttpClient().newCall(new Builder()
                .url("http://localhost:8080" + url).addHeader(hdrKey, hdrVal)
                .get().build()).execute();
    }

    public static void bodyAndResponseCodeShouldBe(okhttp3.Response response, String bodyShouldBe, int rcShouldBe) throws IOException {
        try (response) {
            assertThat(response.body().string(), equalTo(bodyShouldBe));
            assertThat(response.code(), equalTo(rcShouldBe));
        }
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Arrays.asList(NewTests.class, TinyWebTests.class, WebServerTests.class,
                WebSocketTests.class, IntegrationTests.class, DependenciesTests.class, ServerSideEventsTests.class, WithMockitoTests.class,
                ServerCompositionTests.class)), new DefaultReporter());
    }
}
