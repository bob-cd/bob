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
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public class Main {
    private final static Logger logger = LoggerFactory.getLogger(Main.class.getName());

    public static void main(String[] args) {
        final var vertx = Vertx.vertx(new VertxOptions().setHAEnabled(true));

        ConfigRetriever.create(vertx).getConfig(config -> {
            if (config.succeeded()) {
                final var conf = config.result();
                final var rmqConfig = conf.getJsonObject("rabbitmq");
                final var cruxConfig = conf.getJsonObject("crux");
                final var httpConfig = conf.getJsonObject("http");

                final var apiSpec = httpConfig.getString("apiSpec", "/bob/api.yaml");
                final var httpHost = httpConfig.getString("host", "localhost");
                final var httpPort = httpConfig.getInteger("port", 7777);

                logger.info(conf.toString());

                final var queue = RabbitMQClient.create(vertx, new RabbitMQOptions(rmqConfig));
                final var client = WebClient.create(vertx, new WebClientOptions(cruxConfig));

                vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, client), v -> {
                    if (v.succeeded())
                        logger.info(format("Deployed on verticle: %s", v.result()));
                    else
                        logger.error(format("Deployment error: %s", v.cause()));
                });
            }
        });
    }
}
