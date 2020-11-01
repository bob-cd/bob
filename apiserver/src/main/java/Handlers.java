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

import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import com.rabbitmq.client.AMQP;
import crux.api.ICruxAPI;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.rabbitmq.RabbitMQClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static void publishMessage(RabbitMQClient queueClient, String type, String exchange, String routingKey, JsonObject payload) {
        final var props = new AMQP.BasicProperties.Builder().type(type).build();

        queueClient
            .basicPublish(exchange, routingKey, props, payload.toBuffer())
            .onSuccess(_it -> logger.info("Published message with type %s on %s!".formatted(type, routingKey)))
            .onFailure(it -> logger.error("Error publishing message on entities: " + it.getMessage()));
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

    public static void healthCheckHandler(RoutingContext routingContext, RabbitMQClient queue, ICruxAPI node, Vertx vertx) {
        HealthCheck.check(queue, node, vertx)
            .onSuccess(_it ->
                toJsonResponse(routingContext, "Yes we can! \uD83D\uDD28 \uD83D\uDD28", 200)
            )
            .onFailure(err ->
                toJsonResponse(routingContext, err.getMessage(), 503)
            );
    }

    public static void pipelineCreateHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var pipeline = routingContext
            .getBodyAsJson()
            .put("group", group)
            .put("name", name);

        publishMessage(queue, "pipeline/create", "bob.direct", "bob.entities", pipeline);
        toJsonResponse(routingContext, "Ok");
    }

    public static void pipelineDeleteHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var pipeline = new JsonObject()
            .put("group", group)
            .put("name", name);

        publishMessage(queue, "pipeline/delete", "bob.direct", "bob.entities", pipeline);
        toJsonResponse(routingContext, "Ok");
    }

    public static void pipelineStartHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var id = "r-" + UUID.randomUUID();
        final var pipeline = new JsonObject()
            .put("group", group)
            .put("name", name)
            .put("run_id", id);

        publishMessage(queue, "pipeline/start", "bob.direct", "bob.jobs", pipeline);
        toJsonResponse(routingContext, id);
    }

    public static void pipelineStopHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var id = params.get("id");
        final var pipeline = new JsonObject()
            .put("group", group)
            .put("name", name)
            .put("run_id", id);

        publishMessage(queue, "pipeline/stop", "bob.fanout", "", pipeline);
        toJsonResponse(routingContext, "Ok");
    }

    public static void pipelineLogsHandler(RoutingContext routingContext, ICruxAPI node) {
        final var params = routingContext.request().params();
        final var id = params.get("id");
        final var offset = params.get("offset");
        final var lines = params.get("lines");
        final var query = DB.datafy(
            """
            {:find     [(eql/project log [:line]) time]
             :where    [[log :type :log-line]
                        [log :time time]
                        [log :run-id "%s"]]
             :order-by [[time :asc]]
             :limit    %s
             :offset   %s}
            """.formatted(id, lines, offset)
        );
        final var logKey = Keyword.intern(Symbol.create("line"));

        try {
            final var logs = node
                .db()
                .query(query)
                .stream()
                .map(r -> r.get(0))
                .map(r -> ((PersistentArrayMap) r).get(logKey).toString())
                .collect(Collectors.toList());

            toJsonResponse(routingContext, new JsonArray(logs), 200);
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void pipelineStatusHandler(RoutingContext routingContext, ICruxAPI node) {
        final var params = routingContext.request().params();
        final var id = params.get("id");
        final var query = DB.datafy(
            """
            {:find  [(eql/project run [:status])]
             :where [[run :type :pipeline-run]
                     [run :crux.db/id :bob.pipeline.run/%s]]}
            """.formatted(id)
        );
        final var key = Keyword.intern(Symbol.create("status"));

        try {
            final var status = node
                .db()
                .query(query)
                .stream()
                .findFirst()
                .map(r -> r.get(0))
                .map(r -> ((PersistentArrayMap) r).get(key))
                .map(r -> ((Keyword) r).getName());

            if (status.isPresent()) {
                toJsonResponse(routingContext, status.get(), 200);
            } else {
                toJsonResponse(routingContext, "Cannot find status", 404);
            }
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void pipelineArtifactHandler(RoutingContext routingContext, ICruxAPI node, Vertx vertx) {
        final var params = routingContext.request().params();
        final var group = params.get("group");
        final var name = params.get("name");
        final var id = params.get("id");
        final var storeName = params.get("store-name");
        final var artifactName = params.get("artifact-name");

        try {
            final var baseUrl = node
                .db()
                .entity(Keyword.intern("bob.artifact-store", storeName));

            if (baseUrl == null) {
                toJsonResponse(routingContext, "Cannot locate artifact store " + storeName, 404);
                return;
            }

            final var url = String.join(
                "/",
                (String) baseUrl.get(Keyword.intern(Symbol.create("url"))),
                "bob_artifact",
                group,
                name,
                id,
                artifactName
            );

            WebClient.create(vertx)
                .getAbs(url)
                .send()
                .onSuccess(
                    res -> {
                        if (res.statusCode() == 200) {
                            routingContext
                                .response()
                                .putHeader("Content-Type", "application/tar")
                                .end(res.body());
                        } else {
                            toJsonResponse(routingContext, "Error locating artifact " + artifactName, res.statusCode());
                        }
                    }
                )
                .onFailure(err -> toJsonResponse(routingContext, err.getMessage(), 503));
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void pipelineListHandler(RoutingContext routingContext, ICruxAPI node) {
        final var params = routingContext.request().params();
        final var groupClause = params.contains("group") ? "[pipeline :group \"%s\"]".formatted(params.get("group")) : "";
        final var nameClause = params.contains("name") ? "[pipeline :name \"%s\"]".formatted(params.get("name")) : "";
        final var statusClause = params.contains("status") ? "[run :type :pipeline-run] [run :status :%s]".formatted(params.get("status")) : "";
        final var query = DB.datafy(
            """
            {:find  [(eql/project pipeline [:steps :vars :resources :image :group :name])]
             :where [[pipeline :type :pipeline] %s %s %s]}
            """.formatted(groupClause, nameClause, statusClause)
        );

        try {
            final var pipelines = node
                .db()
                .query(query)
                .stream()
                .map(it -> it.get(0))
                .map(DB::toJson)
                .collect(Collectors.toList());

            toJsonResponse(routingContext, pipelines, 200);
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void resourceProviderCreateHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var name = params.get("name");
        final var resourceProvider = routingContext
            .getBodyAsJson()
            .put("name", name);

        publishMessage(queue, "resource-provider/create", "bob.direct", "bob.entities", resourceProvider);
        toJsonResponse(routingContext, "Ok");
    }

    public static void resourceProviderDeleteHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var name = params.get("name");
        final var resourceProvider = new JsonObject().put("name", name);

        publishMessage(queue, "resource-provider/delete", "bob.direct", "bob.entities", resourceProvider);
        toJsonResponse(routingContext, "Ok");
    }

    public static void resourceProviderListHandler(RoutingContext routingContext, ICruxAPI node) {
        final var query = DB.datafy(
            """
            {:find  [(eql/project resource-provider [:name :url])]
             :where [[resource-provider :type :resource-provider]]}
            """
        );

        try {
            final var resourceProvider = node
                .db()
                .query(query)
                .stream()
                .map(it -> it.get(0))
                .map(DB::toJson)
                .collect(Collectors.toList());

            toJsonResponse(routingContext, resourceProvider, 200);
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void artifactStoreCreateHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var name = params.get("name");
        final var artifactStore = routingContext
            .getBodyAsJson()
            .put("name", name);

        publishMessage(queue, "artifact-store/create", "bob.direct", "bob.entities", artifactStore);
        toJsonResponse(routingContext, "Ok");
    }

    public static void artifactStoreDeleteHandler(RoutingContext routingContext, RabbitMQClient queue) {
        final var params = routingContext.request().params();
        final var name = params.get("name");
        final var artifactStore = new JsonObject().put("name", name);

        publishMessage(queue, "artifact-store/delete", "bob.direct", "bob.entities", artifactStore);
        toJsonResponse(routingContext, "Ok");
    }

    public static void artifactStoreListHandler(RoutingContext routingContext, ICruxAPI node) {
        final var query = DB.datafy(
            """
            {:find  [(eql/project artifact-store [:name :url])]
             :where [[artifact-store :type :artifact-store]]}
            """
        );

        try {
            final var artifactStore = node
                .db()
                .query(query)
                .stream()
                .map(it -> it.get(0))
                .map(DB::toJson)
                .collect(Collectors.toList());

            toJsonResponse(routingContext, artifactStore, 200);
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void queryHandler(RoutingContext routingContext, ICruxAPI node) {
        try {
            final var query = DB.datafy(routingContext.request().params().get("q"));
            final var time = routingContext.request().params().get("t");
            final var db = time == null ?
                node.db() :
                node.db(Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(time))));
            final var result = db.query(query);

            routingContext.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(200)
                .end(DB.stringify(result));
        } catch (Exception e) {
            toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }

    public static void errorsHandler(RoutingContext routingContext, RabbitMQClient queue) {
        queue
            .basicGet("bob.errors", true)
            .onSuccess(message -> toJsonResponse(
                routingContext,
                message != null ? message.body().toString() : "No more errors",
                200)
            )
            .onFailure(err -> toJsonResponse(routingContext, err.getMessage(), 500));
    }
}
