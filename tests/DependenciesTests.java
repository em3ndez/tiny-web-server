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
import org.hamcrest.Matchers;

import java.util.regex.Matcher;

import static com.paulhammant.tnywb.TinyWeb.Method.GET;
import static org.forgerock.cuppa.Cuppa.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static tests.Suite.bodyAndResponseCodeShouldBe;
import static tests.Suite.httpGet;

@Test
public class DependenciesTests {
    TinyWeb.Server webServer;
    boolean dependencyException = false;

    {
        describe("When endpoint and filters can depend on components", () -> {
            before(() -> {
                webServer = new TinyWeb.Server(8080, 8081, new TinyWeb.DependencyManager(new TinyWeb.DefaultComponentCache(){{
                    this.put(TinyWebTests.ProductInventory.class, new TinyWebTests.ProductInventory(/* would have secrets in real usage */));
                }}){

                    // This is not Dependency Injection
                    // This also does not use reflection so is fast.

                    @Override
                    public <T> T  instantiateDep(Class<T> clazz, TinyWeb.ComponentCache requestCache, Matcher matcher) {
                        // all your request scoped deps here in a if/else sequence
                        if (clazz == TinyWebTests.ShoppingCart.class) {
                            return (T) TinyWebTests.createOrGetShoppingCart(requestCache);
                        }
                        throw new TinyWeb.DependencyException(clazz);
                        // or ...
//                        return super.instantiateDep(clazz, requestCache);
                    }

                });
                new TinyWeb.ServerComposition(webServer) {

                    {
                    path("/api", () -> {
                        //deps([OrderBook.class]);
                        endPoint(GET, "/howManyOrderInBook", (req, res, ctx) -> {

                            // ShoppingCart is request scoped
                            TinyWebTests.ShoppingCart sc = ctx.dep(TinyWebTests.ShoppingCart.class);

                            TinyWebTests.ShoppingCart sc2 = ctx.dep(TinyWebTests.ShoppingCart.class);

                            res.write("Cart Items before: " + sc.cartCount() + "\n" +
                                    "apple picked: " + sc.pickItem("apple") + "\n" +
                                    "Cart Items after: " + sc.cartCount() + "\n" +
                                    "Shopping cart instance gotten twice is "
                                            + (sc == sc2 ? "the same": "not the same"));
                        });
                        endPoint(GET, "/howBooksInTheInventory", (req, res, ctx) -> {

                            // ProductInventory is application scoped
                            TinyWebTests.ProductInventory pi = null;
                            try {
                                pi = ctx.dep(TinyWebTests.ProductInventory.class);
                                res.write("blah blah never gets here: " + pi.stockItems.size());
                            } catch (TinyWeb.DependencyException e) {
                                // You don't have to try/catch DependencyException as an end-user
                                // you'll discover such things during development, not at deploy-to-production time
                                dependencyException = true;
                                res.write("Oh noes some problem server side: ", 500);
                            }
                        });
                    });
                }};
                webServer.start();

            });
            it("Then it should be able to get dep to function", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/api/howManyOrderInBook"),
                        "Cart Items before: 0\n" +
                                "apple picked: true\n" +
                                "Cart Items after: 1\n" +
                                "Shopping cart instance gotten twice is the same", 200);
                assertThat(dependencyException, Matchers.is(false));
            });
            it("Then it should not be able to depend on items outside request scope", () -> {
                bodyAndResponseCodeShouldBe(httpGet("/api/howBooksInTheInventory"),
"Oh noes some problem server side: ", 500);
                assertThat(dependencyException, Matchers.is(true));
            });
            after(() -> {
                webServer.stop();
                webServer = null;
            });
        });
    }
}
