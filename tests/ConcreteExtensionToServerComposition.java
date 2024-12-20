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

public class ConcreteExtensionToServerComposition extends TinyWeb.ServerComposition {

    public ConcreteExtensionToServerComposition(TinyWeb.WebServer server) {
        super(server);
        path("/bar", () -> {
            endPoint(TinyWeb.Method.GET, "/baz", (req, res, ctx) -> {
                res.write("Hello from (relative) /bar/baz (absolute path: " +req.getPath() + ")");
            });
        });
    }
}
