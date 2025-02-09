/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) Paul Hammant, 2024
 */

package tests;

import com.paulhammant.tiny.Tiny;
import org.forgerock.cuppa.Test;

import java.io.File;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class StaticFilesTests {
    Tiny.WebServer webServer;

    {
        describe("When serving static files", () -> {
            before(() -> {
                webServer = new Tiny.WebServer(Tiny.Config.create().withHostAndWebPort("localhost", 8080).withWebSocketPort(8081)) {{
                    serveStaticFilesAsync("/static", new File(".").getAbsolutePath());
                }};
                webServer.start();
            });
            it("Then it should return 200 and serve a text file", () -> {
                try (okhttp3.Response response = httpGet("/static/BUILDING.md")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().contentType().toString(), equalTo("text/markdown"));
                    assertThat(response.body().string(), containsString("Building with Maven"));
                }
            });
            it("Then it should return 404 for non-existent files", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/static/nonexistent.txt"),
                        "Not found", 404);

            });
            it("Then it should return 200 and serve a file from a subdirectory", () -> {
                try (okhttp3.Response response = httpGet("/static/src/main/java/com/paulhammant/tiny/Tiny.java")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().contentType().toString(), equalTo("text/x-java"));
                    assertThat(response.body().string(), containsString("class"));
                }
            });
            it("Then it should return 200 and serve a non-text file", () -> {
                try (okhttp3.Response response = httpGet("/static/target/classes/com/paulhammant/tiny/Tiny$WebServer.class")) {
                    assertThat(response.code(), equalTo(200));
                    assertThat(response.body().contentType().toString(), equalTo("application/java-vm"));
                    assertThat(response.body().string(), containsString("(Lcom/sun/net/httpserver/HttpExchange;ILjava/lang/String;)V"));
                }
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
