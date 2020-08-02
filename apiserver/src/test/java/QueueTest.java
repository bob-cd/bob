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
        @DisplayName("Create Entities Queue")
        void createEntitiesQueue(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.queueDeclare("entities", true, false, false, it -> {
                    if (it.succeeded()) {
                        serverStarted.flag();
                    } else {
                        testContext.failNow(it.cause());
                    }
                });
            }));
        }

        @Test
        @Order(4)
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
        @Order(5)
        @DisplayName("Consume Create Messages From Queue")
        void consumeMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                vertx.fileSystem().readFile("src/test/resources/createComplexPipeline.payload.json", file -> {
                    if (file.succeeded()) {
                        JsonObject json = new JsonObject(file.result());
                        queue.basicConsumer("entities", (it -> {
                            if (it.succeeded()) {
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
                                rmqConsumer.cancel(cr -> {
                                    if (cr.succeeded()) {
                                        consumerStopped.flag();
                                    } else {
                                        testContext.failNow(cr.cause());
                                    }
                                });
                            } else {
                                testContext.failNow(it.cause());
                            }
                        }));
                    }
                });
            }));
        }

        @Test
        @Order(6)
        @DisplayName("Delete Test Pipeline")
        void deletePipeline(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                for (int i = 0; i < 10; i++) {
                    client.delete("/pipelines/groups/dev/names/test")
                            .send(it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Successfully Deleted Pipeline dev test");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(7)
        @DisplayName("Consume Delete Messages From Queue")
        void consumeDeleteMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("name", "test").put("group", "dev");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("pipeline/delete");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }

        @Test
        @Order(8)
        @DisplayName("Start Test Pipeline")
        void startPipeline(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                for (int i = 0; i < 10; i++) {
                    client.post("/pipelines/start/groups/dev/names/test")
                            .send(it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Successfully Started Pipeline dev test");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(9)
        @DisplayName("Consume Start Messages From Queue")
        void consumeStartMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("name", "test").put("group", "dev");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("pipeline/start");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }

        @Test
        @Order(10)
        @DisplayName("Stop Test Pipeline")
        void stopPipeline(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                for (int i = 0; i < 10; i++) {
                    client.post("/pipelines/stop/groups/dev/names/test/number/42")
                            .send(it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Successfully Stopped Pipeline dev test 42");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(11)
        @DisplayName("Consume Stop Messages From Queue")
        void consumeStopMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("name", "test").put("group", "dev").put("number", "42");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("pipeline/stop");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }

        // TODO PipelineLogs
        // TODO PipelineStatus
        // TODO PipelineArtifactFetch
        // TODO PipelineList

        @Test
        @Order(12)
        @DisplayName("Create Resource Provider")
        void createResourceProvider(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                JsonObject jsonBody = new JsonObject().put("url", "http://foobar:5678");
                for (int i = 0; i < 10; i++) {
                    client.post("/resource-providers/foo")
                            .sendJsonObject(jsonBody, it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Created Resource Provider foo");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(13)
        @DisplayName("Consume Create Resource Provider Messages From Queue")
        void consumeCreateResourceProviderMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("url", "http://foobar:5678").put("name", "foo");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("resource-provider/create");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }

        @Test
        @Order(14)
        @DisplayName("Delete Resource Provider")
        void deleteResourceProvider(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                JsonObject jsonBody = new JsonObject().put("url", "http://foobar:5678");
                for (int i = 0; i < 10; i++) {
                    client.delete("/resource-providers/foo")
                            .send(it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Deleted Resource Provider foo");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(15)
        @DisplayName("Consume Delete Resource Provider Messages From Queue")
        void consumeDeleteResourceProviderMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("name", "foo");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("resource-provider/delete");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }

        @Test
        @Order(16)
        @DisplayName("Create Artifact Store")
        void createArtifactStore(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                JsonObject jsonBody = new JsonObject().put("url", "http://foobar:5678");
                for (int i = 0; i < 10; i++) {
                    client.post("/artifact-stores/foo")
                            .sendJsonObject(jsonBody, it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Created Artifact Store foo");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(17)
        @DisplayName("Consume Create Artifact Store Messages From Queue")
        void consumeCreateArtifactStoreMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("url", "http://foobar:5678").put("name", "foo");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("artifact-store/create");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }

        @Test
        @Order(18)
        @DisplayName("Delete Resource Provider")
        void deleteArtifactStore(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);
            final var client = WebClient.create(vertx, clientConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint requestsServed = testContext.checkpoint(10);

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                JsonObject jsonBody = new JsonObject().put("url", "http://foobar:5678");
                for (int i = 0; i < 10; i++) {
                    client.delete("/artifact-stores/foo")
                            .send(it -> {
                                if (it.failed()) {
                                    testContext.failNow(it.cause());
                                } else {
                                    testContext.verify(() -> {
                                        assertThat(it.result().bodyAsJsonObject().getString("message")).isEqualTo("Deleted Artifact Store foo");
                                        assertThat(it.result().statusCode()).isEqualTo(200);
                                        requestsServed.flag();
                                    });
                                }
                            });
                }
            }));
        }

        @Test
        @Order(19)
        @DisplayName("Consume Delete Artifact Store Messages From Queue")
        void consumeDeleteArtifactStoreMessages(VertxTestContext testContext) {
            final var queue = RabbitMQClient.create(vertx, rabbitConfig);
            final var crux = WebClient.create(vertx, cruxConfig);

            Checkpoint serverStarted = testContext.checkpoint();
            Checkpoint queueStarted = testContext.checkpoint();
            Checkpoint responsesReceived = testContext.checkpoint(10);
            Checkpoint consumerStopped = testContext.checkpoint();

            vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, crux), testContext.succeeding(id -> {
                serverStarted.flag();
                queue.basicConsumer("entities", (it -> {
                    if (it.succeeded()) {
                        queueStarted.flag();
                        RabbitMQConsumer rmqConsumer = it.result();
                        JsonObject pipelinePath = new JsonObject().put("name", "foo");
                        rmqConsumer.handler(message -> {
                            testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(pipelinePath);
                                assertThat((message.properties().getType())).isEqualTo("artifact-store/delete");
                                responsesReceived.flag();
                            });
                        });
                        rmqConsumer.cancel(cr -> {
                            if (cr.succeeded()) {
                                consumerStopped.flag();
                            } else {
                                testContext.failNow(cr.cause());
                            }
                        });
                    } else {
                        testContext.failNow(it.cause());
                    }
                }));
            }));
        }
        @AfterEach
        void cleanup() {
            vertx.close();
        }
    }
}
