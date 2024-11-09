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

import static com.paulhammant.tnywb.TinyWeb.FilterResult.CONTINUE;
import static com.paulhammant.tnywb.TinyWeb.FilterResult.STOP;
import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class FilterTests {
    TinyWeb.Server webServer;

    {
        describe("When passing attributes from filter to endpoint", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, -1) {{
                    path("/api", () -> {
                        filter(".*", (req, res, ctx) -> {
                            String allegedlyLoggedInCookie = req.getCookie("logged-in");
                            // This test class only performs rot47 pn the coolie passed in.
                            // That's not in the secure in the slightest. See https://rot47.net/
                            Authentication auth = IsEncryptedByUs.decrypt(allegedlyLoggedInCookie);
                            if (auth.authentic == false) {
                                res.write("Try logging in again", 403);
                                return STOP;
                            } else {
                                ctx.setAttribute("user", auth.user);
                            }
                            return CONTINUE; // Continue processing
                        });
                        endPoint(GET, "/attribute-test", (req, res, ctx) -> {
                            res.write("User Is logged in: " + ctx.getAttribute("user"));
                        });
                    });
                    start();
                }};
            });

            it("Then the attribute user should be passed from filter to endPoint for authentic user", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/api/attribute-test", "Cookie", "logged-in=7C65o6I2>A=6]4@>"), "User Is logged in: fred@example.com", 200);
            });

            it("Then the attribute user should not be passed from filter to endPoint for inauthentic user", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/api/attribute-test", "Cookie", "logged-in=aeiouaeiou;"), "Try logging in again", 403);
            });

            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
    public static class IsEncryptedByUs {
        public static Authentication decrypt(String allegedlyLoggedInCookie) {
            String rot47ed = rot47(allegedlyLoggedInCookie);
            // check is an email address
            if (rot47ed.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$")) {
                return new Authentication(true, rot47ed);
            } else {
                return new Authentication(false, null);
            }
        }
    }
    public record Authentication(boolean authentic, String user) {}

    private static String rot47(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= '!' && c <= 'O') {
                result.append((char) (c + 47));
            } else if (c >= 'P' && c <= '~') {
                result.append((char) (c - 47));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


}
