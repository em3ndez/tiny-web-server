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

import com.paulhammant.tnywb.TinyWeb;

import org.forgerock.cuppa.Test;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static tests.Suite.httpGet;

@Test
public class NewTests {
    TinyWeb.Server webServer;
    {
        describe("Given a TinyWeb server with ConcreteExtensionToServerComposition", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/a", () -> {
                        path("/b", () -> {
                            path("/c", () -> {
                                new ConcreteExtensionToServerComposition(this);
                            });
                        });
                    });
                }};
                webServer.start();
            });
            describe("When that concrete class is mounted within another path", () -> {
                it("Then endPoints should be able to work relatively", () -> {
                    try (okhttp3.Response response = httpGet("/a/b/c/bar/baz")) {
                        assertThat(response.code(), equalTo(200));
                        assertThat(response.body().string(),
                                equalTo("Hello from (relative) /bar/baz (absolute path: /a/b/c/bar/baz)"));
                    }
                });
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
