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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static tests.Suite.httpGet;

@Test
public class NewTests {
    TinyWeb.Server webServer;

    {

    }
}
        describe("When endpoint and filters can depend on components", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081, new TinyWeb.DependencyManager(new TinyWeb.DefaultComponentCache(){{
                    this.put(TinyWebTests.ProductInventory.class, new TinyWebTests.ProductInventory(/* would have secrets in real usage */));
                }}){

                    // This is not Dependency Injection
                    // This also does not use reflection so is fast.

                    @Override
                    public <T> T  instantiateDep(Class<T> clazz, TinyWeb.ComponentCache requestCache) {
                        if (clazz == TinyWebTests.ShoppingCart.class) {
                            return (T) TinyWebTests.createOrGetShoppingCart(requestCache);
                        }
                        throw new IllegalArgumentException("Unsupported class: " + clazz);
                    }

                });
                //svr.applicationScopeCache.put()
                TinyWebTests.doCompositionForOneTest(webServer);
                webServer.start();

            });
            it("Then it should extract parameters correctly from the path", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/api/howManyOrderInBook"),
                        "Cart Items before: 0\n" +
                        "apple picked: true\n" +
                        "Cart Items after: 1\n", 200);
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
