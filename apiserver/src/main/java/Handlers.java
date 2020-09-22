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

import com.rabbitmq.client.AMQP;
import crux.api.ICruxAPI;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rabbitmq.RabbitMQClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class Handlers {
    private final static Logger logger = LoggerFactory.getLogger(Handlers.class.getName());

    private static void toJsonResponse(RoutingContext routingContext, Object content) {
        toJsonResponse(routingContext, content, 202);
    }

    private static void toJsonResponse(RoutingContext routingContext, Object content, int statusCode) {
        routingContext.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(statusCode)
            .end(new JsonObject(Map.of("message", content)).encode());
    }

    private static void publishToQueue(RabbitMQClient queueClient, String type, String exchange, String queue, JsonObject payload) {
        final var props = new AMQP.BasicProperties.Builder().type(type).build();

        queueClient.basicPublish(exchange, queue, props, payload.toBuffer(), it -> {
            if (it.succeeded())
                logger.info("Published message with type %s on %s!".formatted(type, queue));
            else
                logger.error("Error publishing message on entities: " + it.cause().getMessage());
        });
    }

    public static void apiSpecHandler(RoutingContext routingContext) {
        final var file = new File(Handlers.class.getResource("bob/api.yaml").getFile());

        try {
            routingContext
                .response()
                .putHeader("Content-Type", "application/yaml")
                .end(Files.readString(file.toPath()));
        } catch (IOException e) {
            final var msg = "Could not read spec file: " + e.getMessage();
            logger.error(msg);
            toJsonResponse(routingContext, msg, 500);
        }
    }

    public static void healthCheckHandler(RoutingContext routingContext, RabbitMQClient queue, ICruxAPI node) {
        // TODO use better health check

        if (node.status() != null && queue.isConnected())
            toJsonResponse(routingContext, "Yes we can! \uD83D\uDD28 \uD83D\uDD28", 200);
        else
            toJsonResponse(routingContext, "Failed Health Check", 503);
    }

    public static void pipelineCreateHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var pipeline = routingContext
            .getBodyAsJson()
            .put("group", group)
            .put("name", name);

        publishToQueue(queue, "pipeline/create", "bob.direct", "bob.entities", pipeline);
        toJsonResponse(routingContext, "Ok");
    }

    public static void pipelineDeleteHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var pipeline = new JsonObject()
            .put("group", group)
            .put("name", name);

        publishToQueue(queue, "pipeline/delete", "bob.direct", "bob.entities", pipeline);
        toJsonResponse(routingContext, "Ok");
    }
}
