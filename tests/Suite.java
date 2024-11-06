package tests;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.forgerock.cuppa.Runner;
import org.forgerock.cuppa.reporters.DefaultReporter;

import java.io.IOException;
import java.util.Collections;

public class Suite {

    public static okhttp3.Response httpGet(String url) throws IOException {
        return new OkHttpClient().newCall(new Request.Builder()
                .url("http://localhost:8080" + url)
                .get().build()).execute();
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.run(runner.defineTests(Collections.singletonList(NewTests.class)), new DefaultReporter());
    }
}
