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

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private final static Logger logger = LoggerFactory.getLogger(Main.class.getName());

    public static void main(String[] args) {
        final var vertx = Vertx.vertx(new VertxOptions().setHAEnabled(true));
        final var configRetrieverOptions = new ConfigRetrieverOptions()
            .addStore(new ConfigStoreOptions().setType("env"));

        ConfigRetriever.create(vertx, configRetrieverOptions).getConfig(config -> {
            if (config.succeeded()) {
                final var conf = config.result();

                final var dbName = conf.getString("DB_NAME", "bob");
                final var dbHost = conf.getString("DB_HOST", "localhost");
                final var dbPort = conf.getInteger("DB_PORT", 5432);
                final var dbUser = conf.getString("DB_USER", "bob");
                final var dbPassword = conf.getString("DB_PASSWORD", "bob");

                final var queueHost = conf.getString("QUEUE_HOST", "localhost");
                final var queuePort = conf.getInteger("QUEUE_PORT", 5672);
                final var queueUser = conf.getString("QUEUE_USER", "guest");
                final var queuePassword = conf.getString("QUEUE_PASSWORD", "guest");

                final var apiHost = conf.getString("API_HOST", "0.0.0.0");
                final var apiPort = conf.getInteger("API_PORT", 7777);

                final var node = new DB(dbName, dbHost, dbPort, dbUser, dbPassword).node;
                final var queue = RabbitMQClient.create(
                    vertx,
                    new RabbitMQOptions()
                        .setHost(queueHost)
                        .setPort(queuePort)
                        .setUser(queueUser)
                        .setPassword(queuePassword)
                );

                vertx.deployVerticle(new APIServer("/bob/api.yaml", apiHost, apiPort, queue, node), v -> {
                    if (v.succeeded())
                        logger.info("Deployed on verticle: " + v.result());
                    else
                        logger.error("Deployment error: " + v.cause());
                });
            }
        });
    }
}
