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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Test the Bob API")
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class APIServerTest {
    Vertx vertx;
    final String apiSpec = "/bob/api.yaml";
    final String httpHost = "localhost";
    final int httpPort = 7778;
    final RabbitMQOptions queueConfig = new RabbitMQOptions().setHost("localhost").setPort(5673);
    final ICruxAPI node = new DB("bob-test", "localhost", 5433, "bob", "bob").node;
    final WebClientOptions clientConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(httpPort);

    @BeforeEach
    void prepare() {
        vertx = Vertx.vertx(
            new VertxOptions()
                .setMaxEventLoopExecuteTime(1000)
                .setPreferNativeTransport(true)
        );
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
    @Order(2)
    @DisplayName("Test Pipeline Create")
    void testPipelineCreate(VertxTestContext testContext) {
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
                                assertThat(message.body().toJsonObject()).isEqualTo(json.getJsonObject("pipeline").mergeIn(pipelinePath));
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

    @AfterEach
    void cleanup() {
        vertx.close();
    }
}
