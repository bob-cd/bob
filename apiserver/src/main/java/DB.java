/*
 * This file is part of Bob.
 *
 * Bob is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bob is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bob. If not, see <http://www.gnu.org/licenses/>.
 */


import clojure.java.api.Clojure;
import clojure.lang.IFn;
import crux.api.Crux;
import crux.api.ICruxAPI;
import io.vertx.core.json.JsonObject;

import java.time.Duration;

public class DB {
    public final ICruxAPI node;
    private final static IFn toJson;

    static {
        Clojure.var("clojure.core", "require")
            .invoke(Clojure.read("jsonista.core"));
        toJson = Clojure.var("jsonista.core", "write-value-as-string");
    }

    public DB(String dbName, String dbHost, int dbPort, String dbUser, String dbPassword) {
        final var connectionPool = datafy(
            """
            {:dialect crux.jdbc.psql/->dialect
             :db-spec {:dbname   "%s"
                       :host     "%s"
                       :port     %d
                       :user     "%s"
                       :password "%s"}}
            """.formatted(dbName, dbHost, dbPort, dbUser, dbPassword)
        );

        this.node = Crux.startNode(configurator -> {
            configurator.with("crux/tx-log", txLog -> {
                txLog.module("crux.jdbc/->tx-log");
                txLog.set("connection-pool", connectionPool);
            });
            configurator.with("crux/document-store", docStore -> {
                docStore.module("crux.jdbc/->document-store");
                docStore.set("connection-pool", connectionPool);
            });
        });
        this.node.sync(Duration.ofSeconds(30)); // Become consistent for a max of 30s
    }

    public static Object datafy(String raw) {
        return Clojure.read(raw);
    }

    public static JsonObject toJson(Object data) {
        return new JsonObject((String) toJson.invoke(data));
    }
}
