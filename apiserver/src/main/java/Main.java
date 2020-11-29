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

import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;

public class Main {
    private final static Logger logger = LoggerFactory.getLogger(Main.class.getName());

    private static String getEnv(String key, String def) {
        final var value = System.getenv(key);

        return value == null ? def : value;
    }

    private static int getEnv(String key, int def) {
        final var value = System.getenv(key);

        try {
            return value == null ? def : Integer.parseInt(value);
        } catch (NumberFormatException _e) {
            logger.warn("Reading %s with value %s as an int failed. Defaulting to %d.".formatted(key, value, def));
            return def;
        }
    }

    public static void main(String[] args) throws ConnectException {
        final var vertx = Vertx.vertx();

        final var storageUrl = getEnv("BOB_STORAGE_URL", "jdbc:postgresql://localhost:5432/bob");
        final var storageUser = getEnv("BOB_STORAGE_USER", "bob");
        final var storagePassword = getEnv("BOB_STORAGE_PASSWORD", "bob");
        final var queueUrl = getEnv("BOB_QUEUE_URL", "amqp://localhost:5672");
        final var queueUser = getEnv("BOB_QUEUE_USER", "guest");
        final var queuePassword = getEnv("BOB_QUEUE_PASSWORD", "guest");
        final var apiHost = getEnv("BOB_API_HOST", "0.0.0.0");
        final var apiPort = getEnv("BOB_API_PORT", 7777);
        final var healthCheckFreq = getEnv("BOB_HEALTH_CHECK_FREQ", 5000);
        final var connectionRetryAttempts = getEnv("BOB_CONNECTION_RETRY_ATTEMPTS", 10);
        final var connectionRetryDelay = getEnv("BOB_CONNECTION_RETRY_DELAY", 2000);

        logger.info("Starting APIServer");

        final var node = new DB(
            storageUrl, storageUser, storagePassword, connectionRetryAttempts, connectionRetryDelay
        ).node;
        final var queue = RabbitMQClient.create(
            vertx,
            new RabbitMQOptions()
                .setUri(queueUrl)
                .setUser(queueUser)
                .setPassword(queuePassword)
                .setReconnectAttempts(connectionRetryAttempts)
                .setReconnectInterval(connectionRetryDelay)
        );

        vertx
            .deployVerticle(new APIServer(
                "bob/api.yaml", apiHost, apiPort, queue, node, healthCheckFreq
            ))
            .onSuccess(id -> logger.info("APIServer started, deployed on verticle: " + id))
            .onFailure(err -> {
                logger.error("Deployment error: " + err.getMessage());
                vertx.close();
            });
    }
}
