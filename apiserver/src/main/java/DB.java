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

import java.net.ConnectException;
import java.time.Duration;

public class DB {
    public final ICruxAPI node;
    private final static IFn toJson;

    static {
        Clojure.var("clojure.core", "require")
            .invoke(Clojure.read("jsonista.core"));
        toJson = Clojure.var("jsonista.core", "write-value-as-string");
    }

    public DB(
        String dbUrl,
        String dbUser,
        String dbPassword,
        int connectionRetryAttempts,
        int connectionRetryDelay
    ) throws ConnectException {
        final var connectionPool = datafy(
            """
            {:dialect crux.jdbc.psql/->dialect
             :db-spec {:jdbcUrl  "%s"
                       :user     "%s"
                       :password "%s"}}
            """.formatted(dbUrl, dbUser, dbPassword)
        );

        ICruxAPI connectedNode = null;
        while (connectionRetryAttempts > 0) {
            try {
                connectedNode = Crux.startNode(configurator -> {
                    configurator.with("crux/tx-log", txLog -> {
                        txLog.module("crux.jdbc/->tx-log");
                        txLog.set("connection-pool", connectionPool);
                    });
                    configurator.with("crux/document-store", docStore -> {
                        docStore.module("crux.jdbc/->document-store");
                        docStore.set("connection-pool", connectionPool);
                    });
                });

                break;
            } catch (Exception e) {
                System.err.printf(
                    "DB connection failed with %s, retrying %d.%n", e.getMessage(), connectionRetryAttempts
                );
                connectionRetryAttempts--;
                try {
                    Thread.sleep(connectionRetryDelay);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        if (connectionRetryAttempts == 0)
            throw new ConnectException("Could not connect to DB");

        this.node = connectedNode;
        this.node.sync(Duration.ofSeconds(30)); // Become consistent for a max of 30s
    }

    public static Object datafy(String raw) {
        return Clojure.read(raw);
    }

    public static JsonObject toJson(Object data) {
        return new JsonObject(stringify(data));
    }

    public static String stringify(Object data) {
        return toJson.invoke(data).toString();
    }
}
