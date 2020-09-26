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

                final var dbName = conf.getString("BOB_STORAGE_DATABASE", "bob");
                final var dbHost = conf.getString("BOB_STORAGE_HOST", "localhost");
                final var dbPort = conf.getInteger("BOB_STORAGE_PORT", 5432);
                final var dbUser = conf.getString("BOB_STORAGE_USER", "bob");
                final var dbPassword = conf.getString("BOB_STORAGE_PASSWORD", "bob");

                final var queueHost = conf.getString("BOB_QUEUE_HOST", "localhost");
                final var queuePort = conf.getInteger("BOB_QUEUE_PORT", 5672);
                final var queueUser = conf.getString("BOB_QUEUE_USER", "guest");
                final var queuePassword = conf.getString("BOB_QUEUE_PASSWORD", "guest");

                final var apiHost = conf.getString("BOB_API_HOST", "0.0.0.0");
                final var apiPort = conf.getInteger("BOB_API_PORT", 7777);

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
