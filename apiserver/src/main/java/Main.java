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

import crux.api.ICruxAPI;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;

public class Main {
    private final static Logger logger = LoggerFactory.getLogger(Main.class.getName());

    public static void main(String[] args) {
        final var vertx = Vertx.vertx(new VertxOptions().setHAEnabled(true));
        final var configRetrieverOptions = new ConfigRetrieverOptions()
            .addStore(new ConfigStoreOptions().setType("env"));

        ConfigRetriever
            .create(vertx, configRetrieverOptions)
            .getConfig()
            .compose(conf -> {
                final var storageUrl = conf.getString("BOB_STORAGE_URL", "jdbc:postgresql://localhost:5432/bob");
                final var storageUser = conf.getString("BOB_STORAGE_USER", "bob");
                final var storagePassword = conf.getString("BOB_STORAGE_PASSWORD", "bob");

                final var queueUrl = conf.getString("BOB_QUEUE_URL", "amqp://localhost:5672");
                final var queueUser = conf.getString("BOB_QUEUE_USER", "guest");
                final var queuePassword = conf.getString("BOB_QUEUE_PASSWORD", "guest");

                final var apiHost = conf.getString("BOB_API_HOST", "0.0.0.0");
                final var apiPort = conf.getInteger("BOB_API_PORT", 7777);
                final var healthCheckFreq = conf.getInteger("BOB_HEALTH_CHECK_FREQ", 5000);
                final var connectionRetryAttempts = conf.getInteger("BOB_CONNECTION_RETRY_ATTEMPTS", 10);
                final var connectionRetryDelay = conf.getInteger("BOB_CONNECTION_RETRY_DELAY", 2000);

                final ICruxAPI node;
                try {
                    node = new DB(
                        storageUrl, storageUser, storagePassword, connectionRetryAttempts, connectionRetryDelay
                    ).node;
                } catch (ConnectException e) {
                    return Future.failedFuture(e);
                }

                final var queue = RabbitMQClient.create(
                    vertx,
                    new RabbitMQOptions()
                        .setUri(queueUrl)
                        .setUser(queueUser)
                        .setPassword(queuePassword)
                        .setReconnectAttempts(connectionRetryAttempts)
                        .setReconnectInterval(connectionRetryDelay)
                );

                return vertx.deployVerticle(new APIServer(
                    "bob/api.yaml", apiHost, apiPort, queue, node, healthCheckFreq
                ));
            })
            .onSuccess(id -> logger.info("Deployed on verticle: " + id))
            .onFailure(err -> {
                logger.error("Deployment error: " + err.getMessage());
                vertx.close();
            });
    }
}
