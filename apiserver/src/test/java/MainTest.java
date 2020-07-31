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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("👋 A fairly basic test example")
@ExtendWith(VertxExtension.class)
class APIServerTest {

    @Test
    @DisplayName("🚀 Deploy a HTTP service verticle and make 10 requests")
    void useAPIServer(Vertx vertx, VertxTestContext testContext) {
        Checkpoint deploymentCheckpoint = testContext.checkpoint();
        Checkpoint requestCheckpoint = testContext.checkpoint(10);
        final var apiSpec = "/bob/api.yaml";
        final var httpHost = "localhost";
        final var httpPort = 17777;

        final var rabbitConfig = new RabbitMQOptions().setHost("localhost").setPort(5673);
        final var queue = RabbitMQClient.create(vertx, rabbitConfig);

        final var cruxConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(7779);
        final var crux = WebClient.create(vertx, cruxConfig);

        final var clientConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(17777);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
            deploymentCheckpoint.flag();
            for (int i = 0; i < 10; i++) {
                client.get("/api.yaml")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertThat(resp.statusCode()).isEqualTo(200);
                            assertThat(resp.body()).contains("title: Bob the Builder");
                            requestCheckpoint.flag();
                        });
                    }));
            }
        }));
    }

    @DisplayName("➡️ A nested test with customized lifecycle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @Nested
    class CustomLifecycleTest {

        Vertx vertx;

        String apiSpec = "/bob/api.yaml";
        String httpHost = "localhost";
        Integer httpPort = 17777;

        RabbitMQOptions rabbitConfig = new RabbitMQOptions().setHost("localhost").setPort(5673);
        WebClientOptions cruxConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(7779);
        WebClientOptions clientConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(17777);

        @BeforeEach
        void prepare() {
            vertx = Vertx.vertx(new VertxOptions()
                .setMaxEventLoopExecuteTime(1000)
                .setPreferNativeTransport(true));
        }

        @Test
        @Order(1)
        @DisplayName("⬆️ Deploy APIServer")
        void deployAPIServer(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id ->
                testContext.completeNow()));
        }

        @Test
        @Order(2)
        @DisplayName("🛂 Test Health Check of APIServer")
        void httpRequest(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                client.get("/can-we-build-it")
                    .as(BodyCodec.string())
                    .send(ar -> {
                        if (ar.failed()) {
                            testContext.failNow(ar.cause());
                        } else {
                            testContext.verify(() -> {
                                assertThat(ar.result().statusCode()).isEqualTo(200);
                                assertThat(ar.result().body()).contains("Yes we can!");
                                testContext.completeNow();
                            });
                        }
                    });
            }));
        }

        @Test
        @Order(3)
        @DisplayName("Create Test Pipeline")
        void createPipeline(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                vertx.fileSystem().readFile("src/test/resources/createComplexPipeline.payload.json", file -> {
                    if (file.succeeded()) {
                        JsonObject json = new JsonObject(file.result());
                        for (int i = 0; i < 10; i++) {
                            client.post("/pipelines/groups/dev/names/test")
                                .putHeader("Content-Type", "application/json")
                                .putHeader("content-length", "52")
                                .sendJsonObject(json, ar -> {
                                    if (ar.failed()) {
                                        testContext.failNow(ar.cause());
                                    } else {
                                        testContext.verify(() -> {
                                            assertThat(ar.result().bodyAsJsonObject().getString("message")).isEqualTo("Successfully Created Pipeline dev test");
                                            assertThat(ar.result().statusCode()).isEqualTo(200);
                                            requestsServed.flag();
                                        });
                                    }
                                });
                        }
                    }
                });
            }));
        }

        @Test
        @Order(4)
        @DisplayName("Consume Messages From Queue")
        void consumeMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                vertx.fileSystem().readFile("src/test/resources/createComplexPipeline.payload.json", file -> {
                    if (file.succeeded()) {
                        JsonObject json = new JsonObject(file.result());
                        queue.basicConsumer("entities", (it -> {
                            if (it.succeeded()) {
                                System.out.println("RabbitMQ Consumer started!");
                                queueStarted.flag();
                                RabbitMQConsumer rmqConsumer = it.result();
                                JsonObject pipelinePath = new JsonObject().put("name", "test").put("group", "dev");
                                rmqConsumer.handler(message -> {
                                    testContext.verify(() -> {
                                        assertThat(message.body().toJsonObject()).isEqualTo(json.mergeIn(pipelinePath));
                                        assertThat((message.properties().getType())).isEqualTo("pipeline/create");
                                        responsesReceived.flag();
                                    });
                                });
                            } else {
                                System.out.println(format("Cannot start Consumer: %s", it.cause()));
                                testContext.failNow(it.cause());
                            }
                        }));
                    }
                });
            }));
        }

        @AfterEach
        void cleanup() {
            vertx.close();
        }
    }
}
