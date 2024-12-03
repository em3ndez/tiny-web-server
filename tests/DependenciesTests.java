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

import java.util.HashMap;
import java.util.Map;
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
                TinyWeb.DefaultComponentCache cache = new TinyWeb.DefaultComponentCache();
                cache.put(ProductInventory.class, new ProductInventory(/* would have secrets in real usage */));
                webServer = new TinyWeb.Server(8080, 8081, new TinyWeb.DependencyManager(cache){

                    // Note: this is not Dependency Injection

                    @Override
                    public <T> T  instantiateDep(Class<T> clazz, TinyWeb.ComponentCache requestCache, Matcher matcher) {
                        // all your request scoped deps here in a if/else sequence
                        if (clazz == ShoppingCart.class) {
                            return (T) createOrGetShoppingCart(requestCache);
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
                            ShoppingCart sc = ctx.dep(ShoppingCart.class);

                            ShoppingCart sc2 = ctx.dep(ShoppingCart.class);

                            res.write("Cart Items before: " + sc.cartCount() + "\n" +
                                    "apple picked: " + sc.pickItem("apple") + "\n" +
                                    "Cart Items after: " + sc.cartCount() + "\n" +
                                    "Shopping cart instance gotten twice is "
                                            + (sc == sc2 ? "the same": "not the same"));
                        });
                        endPoint(GET, "/howBooksInTheInventory", (req, res, ctx) -> {

                            // ProductInventory is application scoped
                            ProductInventory pi = null;
                            try {
                                pi = ctx.dep(ProductInventory.class);
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


    public static class ProductInventory {

        // We should not hard code cart contents in real life - see note about database below.
        Map<String, Integer> stockItems = new HashMap<>() {{
            put("apple", 100);
            put("orange", 50);
            put("bagged bannana", 33);
        }};

        public boolean customerReserves(String item) {
            if (stockItems.containsKey(item)) {
                if (stockItems.get(item) > 0) {
                    stockItems.put(item, stockItems.get(item) -1);
                    return true;
                }
            }
            return false;
        }
    }

    /*
      Most likely the real version of this would use a database to go get the shopping cart contents
      for the user. Or some database-like solution, that aids quick "session" re-acquisition.
     */
    public static class ShoppingCart {

        private final ProductInventory inv;
        private final Map<String, Integer> items = new HashMap<>();

        public ShoppingCart(ProductInventory inv) {
            this.inv = inv;
        }

        public int cartCount() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean pickItem(String item) {
            boolean gotIt = inv.customerReserves(item);
            if (!gotIt) {
                return false;
            }
            if (items.containsKey(item)) {
                items.put(item, items.get(item) +1);
            } else {
                items.put(item, 1);
            }
            return true;
        }
    }


    public static ShoppingCart createOrGetShoppingCart(TinyWeb.ComponentCache cache) {
        return cache.getOrCreate(ShoppingCart.class, () ->
                new ShoppingCart(getOrCreateProductInventory(cache))
        );
    }

    public static ProductInventory getOrCreateProductInventory(TinyWeb.ComponentCache cache) {
        return cache.getParent().getOrCreate(ProductInventory.class, ProductInventory::new);
    }
    
}
