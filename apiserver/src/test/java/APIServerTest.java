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
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Test the Bob API")
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class APIServerTest {
    Vertx vertx;
    Connection conn;

    final String apiSpec = "/bob/api.yaml";
    final String httpHost = "localhost";
    final int httpPort = 7778;
    final RabbitMQOptions queueConfig = new RabbitMQOptions().setHost("localhost").setPort(5673);
    final ICruxAPI node = new DB("bob-test", "localhost", 5433, "bob", "bob").node;
    final WebClientOptions clientConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(httpPort);

    @BeforeEach
    void prepare() throws SQLException {
        vertx = Vertx.vertx(
            new VertxOptions()
                .setMaxEventLoopExecuteTime(1000)
                .setPreferNativeTransport(true)
        );
        conn = DriverManager.getConnection("jdbc:postgresql://localhost:5433/bob-test?user=bob&password=bob");
    }

    @Test
    @Order(1)
    @DisplayName("Test API Spec path")
    void testAPISpecPath(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client.get("/api.yaml")
                .as(BodyCodec.string())
                .send(ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            final var result = ar.result();

                            assertThat(result.statusCode()).isEqualTo(200);
                            assertThat(result.getHeader("Content-Type")).isEqualTo("application/yaml");

                            testContext.completeNow();
                        });
                    }
                })));
    }

    @Test
    @Order(2)
    @DisplayName("Test Health Check")
    void testHealthCheck(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client.get("/can-we-build-it")
                .send(ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            final var result = ar.result();

                            assertThat(result.statusCode()).isEqualTo(200);
                            assertThat(result.getHeader("Content-Type")).isEqualTo("application/json");
                            assertThat(result.bodyAsJsonObject().getString("message")).isEqualTo("Yes we can! 🔨 🔨");

                            testContext.completeNow();
                        });
                    }
                })));
    }

    @Test
    @Order(3)
    @DisplayName("Test Pipeline Create")
    void testPipelineCreateSuccess(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            vertx.fileSystem().readFile("src/test/resources/createComplexPipeline.payload.json", file -> {
                if (file.succeeded()) {
                    final var json = new JsonObject(file.result());

                    queue.basicConsumer("bob.entities", new QueueOptions().setAutoAck(true), it -> {
                        if (it.succeeded()) {
                            final var rmqConsumer = it.result();
                            final var pipelinePath = new JsonObject()
                                .put("name", "test")
                                .put("group", "dev");

                            rmqConsumer.handler(message -> testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(json.mergeIn(pipelinePath));
                                assertThat(message.properties().getType()).isEqualTo("pipeline/create");

                                testContext.completeNow();
                            }));
                        } else {
                            testContext.failNow(it.cause());
                        }
                    });

                    client.post("/pipelines/groups/dev/names/test")
                        .putHeader("Content-Type", "application/json")
                        .sendJsonObject(json, ar -> {
                            if (ar.failed()) {
                                testContext.failNow(ar.cause());
                            } else {
                                testContext.verify(() -> {
                                    assertThat(ar.result().bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                                    assertThat(ar.result().statusCode()).isEqualTo(202);
                                });
                            }
                        });
                } else {
                    testContext.failNow(file.cause());
                }
            })));
    }

    @Test
    @Order(4)
    @DisplayName("Test Pipeline Create Failure")
    void testPipelineCreateFailure(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client.post("/pipelines/groups/dev/names/test")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(new JsonObject(), ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            assertThat(ar.result().statusCode()).isEqualTo(400);

                            testContext.completeNow();
                        });
                    }
                })));
    }

    @Test
    @Order(5)
    @DisplayName("Test Pipeline Deletion")
    void testPipelineDeletion(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var payload = new JsonObject()
            .put("name", "test")
            .put("group", "dev");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id -> {
            queue.basicConsumer("bob.entities", new QueueOptions().setAutoAck(true), it -> {
                if (it.succeeded()) {
                    final var rmqConsumer = it.result();

                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(payload);
                        assertThat(message.properties().getType()).isEqualTo("pipeline/delete");

                        testContext.completeNow();
                    }));
                } else {
                    testContext.failNow(it.cause());
                }
            });

            client.delete("/pipelines/groups/dev/names/test")
                .putHeader("Content-Type", "application/json")
                .send(ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            assertThat(ar.result().bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                            assertThat(ar.result().statusCode()).isEqualTo(202);
                        });
                    }
                });
        }));
    }

    @Test
    @Order(6)
    @DisplayName("Test Pipeline Start")
    void testPipelineStart(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var payload = new JsonObject()
            .put("name", "test")
            .put("group", "dev");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id -> {
            queue.basicConsumer("bob.jobs", new QueueOptions().setAutoAck(true), it -> {
                if (it.succeeded()) {
                    final var rmqConsumer = it.result();

                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(payload);
                        assertThat(message.properties().getType()).isEqualTo("pipeline/start");

                        testContext.completeNow();
                    }));
                } else {
                    testContext.failNow(it.cause());
                }
            });

            client.post("/pipelines/start/groups/dev/names/test")
                .send(ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            assertThat(ar.result().bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                            assertThat(ar.result().statusCode()).isEqualTo(202);
                        });
                    }
                });
        }));
    }

    @Test
    @Order(7)
    @DisplayName("Test Pipeline Stop")
    void testPipelineStop(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var payload = new JsonObject()
            .put("name", "test")
            .put("group", "dev")
            .put("run_id", "a-run-id");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .queueDeclare("bob.test.broadcasts", true, true, true)
                .compose(_it -> queue.queueBind("bob.test.broadcasts", "bob.fanout", ""))
                .onSuccess(_it -> {
                    queue.basicConsumer("bob.test.broadcasts", new QueueOptions().setAutoAck(true), c -> {
                        if (c.succeeded()) {
                            final var rmqConsumer = c.result();

                            rmqConsumer.handler(message -> testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(payload);
                                assertThat(message.properties().getType()).isEqualTo("pipeline/stop");

                                testContext.completeNow();
                            }));
                        } else {
                            testContext.failNow(c.cause());
                        }
                    });

                    client
                        .post("/pipelines/stop/groups/dev/names/test/runs/a-run-id")
                        .send(ar -> {
                            if (ar.failed()) {
                                testContext.failNow(ar.cause());
                            } else {
                                testContext.verify(() -> {
                                    assertThat(ar.result().bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                                    assertThat(ar.result().statusCode()).isEqualTo(202);
                                });
                            }
                        });
                })
                .onFailure(testContext::failNow)));
    }

    @Test
    @Order(8)
    @DisplayName("Test Log Fetch")
    void testLogFetch(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var expectedLogs = new JsonArray()
            .add("line 1")
            .add("line 2")
            .add("line 3")
            .add("line 4")
            .add("line 5");
        final var query =
            """
            [[:crux.tx/put
              {:crux.db/id :bob.pipeline.log/l-%s
               :type       :log-line
               :time       #inst "%s"
               :run-id     "a-run-id"
               :line       "%s"}]]
            """;

        expectedLogs.forEach(log ->
            node.awaitTx(
                node.submitTx((List<List<?>>) DB.datafy(query.formatted(UUID.randomUUID(), Instant.now(), log))),
                Duration.ofSeconds(5)
            )
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client.get("/pipelines/logs/runs/a-run-id/offset/0/lines/5")
                .send(ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            final var result = ar.result();

                            assertThat(result.statusCode()).isEqualTo(200);
                            assertThat(result.getHeader("Content-Type")).isEqualTo("application/json");
                            assertThat(result.bodyAsJsonObject().getJsonArray("message")).isEqualTo(expectedLogs);

                            testContext.completeNow();
                        });
                    }
                })));
    }

    @Test
    @Order(9)
    @DisplayName("Test Pipeline status")
    void testPipelineStatus(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.pipeline.run/l-a-run-id
               :type       :pipeline-run
               :group      "dev"
               :name       "test"
               :status     :running}]]
            """
        );

        node.awaitTx(
            node.submitTx((List<List<?>>) query),
            Duration.ofSeconds(5)
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client.get("/pipelines/status/runs/a-run-id")
                .send(ar -> {
                    if (ar.failed()) {
                        testContext.failNow(ar.cause());
                    } else {
                        testContext.verify(() -> {
                            final var result = ar.result();

                            assertThat(result.statusCode()).isEqualTo(200);
                            assertThat(result.getHeader("Content-Type")).isEqualTo("application/json");
                            assertThat(result.bodyAsJsonObject().getString("message")).isEqualTo("running");

                            testContext.completeNow();
                        });
                    }
                })));
    }

    @AfterEach
    void cleanup() throws SQLException {
        final var st = conn.createStatement();
        st.execute("DROP TABLE tx_events;");
        st.close();
        conn.close();
        vertx.close();
    }
}
