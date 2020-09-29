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
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
public class APIServerTest {
    Vertx vertx;
    Connection conn;
    ICruxAPI node;

    final String apiSpec = "/bob/api.yaml";
    final String httpHost = "localhost";
    final int httpPort = 7778;
    final RabbitMQOptions queueConfig = new RabbitMQOptions().setHost("localhost").setPort(5673);
    final WebClientOptions clientConfig = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(httpPort);

    @BeforeEach
    void prepare() throws SQLException {
        vertx = Vertx.vertx(
            new VertxOptions()
                .setMaxEventLoopExecuteTime(1000)
                .setPreferNativeTransport(true)
        );

        node = new DB("bob-test", "localhost", 5433, "bob", "bob").node;
        conn = DriverManager.getConnection("jdbc:postgresql://localhost:5433/bob-test?user=bob&password=bob");
    }

    @Test
    @DisplayName("Test API Spec path")
    void testAPISpecPath(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/api.yaml")
                .as(BodyCodec.string())
                .send()
                .onSuccess(res -> testContext.verify(() -> {

                        assertThat(res.statusCode()).isEqualTo(200);
                        assertThat(res.getHeader("Content-Type")).isEqualTo("application/yaml");

                        testContext.completeNow();
                    })
                )
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Health Check")
    void testHealthCheck(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/can-we-build-it")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                        assertThat(res.statusCode()).isEqualTo(200);
                        assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                        assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Yes we can! 🔨 🔨");

                        testContext.completeNow();
                    })
                )
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Pipeline Create")
    void testPipelineCreateSuccess(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            vertx.fileSystem().readFile("src/test/resources/createComplexPipeline.payload.json", file -> {
                if (file.succeeded()) {
                    final var json = new JsonObject(file.result());

                    queue
                        .basicConsumer("bob.entities", new QueueOptions().setAutoAck(true))
                        .onSuccess(rmqConsumer -> {
                            final var pipelinePath = new JsonObject()
                                .put("name", "test")
                                .put("group", "dev");

                            rmqConsumer.handler(message -> testContext.verify(() -> {
                                assertThat(message.body().toJsonObject()).isEqualTo(json.mergeIn(pipelinePath));
                                assertThat(message.properties().getType()).isEqualTo("pipeline/create");

                                testContext.completeNow();
                            }));
                        })
                        .compose(_it -> client.post("/pipelines/groups/dev/names/test")
                            .putHeader("Content-Type", "application/json")
                            .sendJsonObject(json))
                        .onSuccess(res -> testContext.verify(() -> {
                            assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                            assertThat(res.statusCode()).isEqualTo(202);
                        }))
                        .onFailure(testContext::failNow);
                } else {
                    testContext.failNow(file.cause());
                }
            })));
    }

    @Test
    @DisplayName("Test Pipeline Create Failure")
    void testPipelineCreateFailure(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .post("/pipelines/groups/dev/names/test")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(new JsonObject())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(400);

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Pipeline Deletion")
    void testPipelineDeletion(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var payload = new JsonObject()
            .put("name", "test")
            .put("group", "dev");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .basicConsumer("bob.entities", new QueueOptions().setAutoAck(true))
                .onSuccess(rmqConsumer -> rmqConsumer.handler(message -> testContext.verify(() -> {
                    assertThat(message.body().toJsonObject()).isEqualTo(payload);
                    assertThat(message.properties().getType()).isEqualTo("pipeline/delete");

                    testContext.completeNow();
                })))
                .compose(_it -> client.delete("/pipelines/groups/dev/names/test")
                    .putHeader("Content-Type", "application/json")
                    .send())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Pipeline Start")
    void testPipelineStart(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var payload = new JsonObject()
            .put("name", "test")
            .put("group", "dev");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .basicConsumer("bob.jobs", new QueueOptions().setAutoAck(true))
                .onSuccess(rmqConsumer ->
                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(payload);
                        assertThat(message.properties().getType()).isEqualTo("pipeline/start");

                        testContext.completeNow();
                    })))
                .compose(_it -> client.post("/pipelines/start/groups/dev/names/test")
                    .send())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
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
                .compose(_it -> queue.basicConsumer("bob.test.broadcasts", new QueueOptions().setAutoAck(true)))
                .onSuccess(rmqConsumer ->
                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(payload);
                        assertThat(message.properties().getType()).isEqualTo("pipeline/stop");

                        testContext.completeNow();
                    })))
                .compose(_it -> client
                    .post("/pipelines/stop/groups/dev/names/test/runs/a-run-id")
                    .send())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
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
            client
                .get("/pipelines/logs/runs/a-run-id/offset/0/lines/5")
                .send()
                .onSuccess(res ->
                    testContext.verify(() -> {
                        assertThat(res.statusCode()).isEqualTo(200);
                        assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                        assertThat(res.bodyAsJsonObject().getJsonArray("message")).isEqualTo(expectedLogs);

                        testContext.completeNow();
                    }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Pipeline status")
    void testPipelineStatus(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.pipeline.run/a-run-id
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
            client
                .get("/pipelines/status/runs/a-run-id")
                .send()
                .onSuccess(res ->
                    testContext.verify(() -> {
                        assertThat(res.statusCode()).isEqualTo(200);
                        assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                        assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("running");

                        testContext.completeNow();
                    }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Failed Pipeline status")
    void testFailedPipelineStatus(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/pipelines/status/runs/another-run-id")
                .send()
                .onSuccess(res ->
                    testContext.verify(() -> {
                        assertThat(res.statusCode()).isEqualTo(404);
                        assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                        assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Cannot find status");

                        testContext.completeNow();
                    }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Pipeline Artifact Fetch")
    void testPipelineArtifactFetch(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.artifact-store/local
               :type       :artifact-store
               :url        "http://localhost:8001"}]]
            """
        );

        node.awaitTx(
            node.submitTx((List<List<?>>) query),
            Duration.ofSeconds(5)
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id -> {
            final var form = MultipartForm.create()
                .binaryFileUpload("data", "test.tar", "src/test/resources/test.tar", "application/tar");

            client
                .post(8001, "localhost", "/bob_artifact/dev/test/a-run-id/file")
                .sendMultipartForm(form)
                .compose(_it -> client
                    .get("/pipelines/groups/dev/names/test/runs/a-run-id/artifact-stores/local/artifact/file")
                    .send())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/tar");

                    testContext.completeNow();
                }))
                .onSuccess(_it -> client
                    .delete(8001, "localhost", "/bob_artifact/dev/test/a-run-id/file")
                    .send()
                )
                .onFailure(testContext::failNow);
        }));
    }

    @Test
    @DisplayName("Test Failed Pipeline Artifact Fetch With No Store")
    void testFailedPipelineArtifactFetchWithNoStore(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/pipelines/groups/dev/names/test/runs/a-run-id/artifact-stores/local/artifact/file")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(404);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Cannot locate artifact store local");

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Failed Pipeline Artifact Fetch With No Artifact")
    void testFailedPipelineArtifactFetchWithNoArtifact(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.artifact-store/local
               :type       :artifact-store
               :url        "http://localhost:8001"}]]
            """
        );

        node.awaitTx(
            node.submitTx((List<List<?>>) query),
            Duration.ofSeconds(5)
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/pipelines/groups/dev/names/test/runs/a-run-id/artifact-stores/local/artifact/file")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(404);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Error locating artifact file");

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Pipelines List")
    void testPipelinesList(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.pipeline.dev/test1
               :type       :pipeline
               :group      "dev"
               :name       "test1"
               :image      "busybox:musl"
               :steps      [{:cmd "echo yes"}]}]
             [:crux.tx/put
              {:crux.db/id :bob.pipeline.dev/test2
               :type       :pipeline
               :group      "dev"
               :name       "test2"
               :image      "alpine:latest"
               :steps      [{:cmd "echo yesnt"}]}]
             [:crux.tx/put
              {:crux.db/id :bob.pipeline.prod/test1
               :type       :pipeline
               :group      "prod"
               :name       "test1"
               :image      "alpine:latest"
               :steps      [{:cmd "echo boo"}]}]]
            """
        );

        node.awaitTx(
            node.submitTx((List<List<?>>) query),
            Duration.ofSeconds(5)
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/pipelines")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getJsonArray("message")).hasSize(3); // TODO: Check the actual objects

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Resource Provider Create")
    void testResourceProviderCreateSuccess(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var json = new JsonObject().put("url", "http://my-resource-provider");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .basicConsumer("bob.entities", new QueueOptions().setAutoAck(true))
                .onSuccess(rmqConsumer -> {
                    final var resourceProvider = new JsonObject()
                        .put("name", "my-resource-provider")
                        .put("url", "http://my-resource-provider");

                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(json.mergeIn(resourceProvider));
                        assertThat(message.properties().getType()).isEqualTo("resource-provider/create");

                        testContext.completeNow();
                    }));
                })
                .compose(_it -> client.post("/resource-providers/my-resource-provider")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(json))
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Resource Provider Delete")
    void testResourceProviderDeleteSuccess(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .basicConsumer("bob.entities", new QueueOptions().setAutoAck(true))
                .onSuccess(rmqConsumer -> {
                    final var resourceProvider = new JsonObject()
                        .put("name", "my-resource-provider");

                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(resourceProvider);
                        assertThat(message.properties().getType()).isEqualTo("resource-provider/delete");

                        testContext.completeNow();
                    }));
                })
                .compose(_it -> client.delete("/resource-providers/my-resource-provider")
                    .putHeader("Content-Type", "application/json")
                    .send())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Resource Provider Empty List")
    void testResourceProviderEmptyList(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/resource-providers")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getJsonArray("message")).hasSize(0); // TODO: Check the actual objects

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Resource Provider List")
    void testResourceProviderList(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.resource-provider.dev/test1
               :type       :resource-provider
               :name       "test1"
               :url        "http://localhost:8000"}]
             [:crux.tx/put
              {:crux.db/id :bob.resource-provider.dev/test2
               :type       :resource-provider
               :name       "test2"
               :url        "http://localhost:8001"}]]
            """
        );

        node.awaitTx(
            node.submitTx((List<List<?>>) query),
            Duration.ofSeconds(5)
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/resource-providers")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getJsonArray("message")).hasSize(2); // TODO: Check the actual objects

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Artifact Store Create")
    void testArtifactStoreCreateSuccess(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var json = new JsonObject().put("url", "http://my-artifact-store");

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .basicConsumer("bob.entities", new QueueOptions().setAutoAck(true))
                .onSuccess(rmqConsumer -> {
                    final var artifactStore = new JsonObject()
                        .put("name", "my-artifact-store")
                        .put("url", "http://my-artifact-store");

                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(json.mergeIn(artifactStore));
                        assertThat(message.properties().getType()).isEqualTo("artifact-store/create");

                        testContext.completeNow();
                    }));
                })
                .compose(_it -> client.post("/artifact-stores/my-artifact-store")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(json))
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Artifact Store Delete")
    void testArtifactStoreDeleteSuccess(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            queue
                .basicConsumer("bob.entities", new QueueOptions().setAutoAck(true))
                .onSuccess(rmqConsumer -> {
                    final var artifactStore = new JsonObject()
                        .put("name", "my-artifact-store");

                    rmqConsumer.handler(message -> testContext.verify(() -> {
                        assertThat(message.body().toJsonObject()).isEqualTo(artifactStore);
                        assertThat(message.properties().getType()).isEqualTo("artifact-store/delete");

                        testContext.completeNow();
                    }));
                })
                .compose(_it -> client.delete("/artifact-stores/my-artifact-store")
                    .putHeader("Content-Type", "application/json")
                    .send())
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getString("message")).isEqualTo("Ok");
                    assertThat(res.statusCode()).isEqualTo(202);
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Artifact Store Empty List")
    void testArtifactStoreEmptyList(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/artifact-stores")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getJsonArray("message")).hasSize(0); // TODO: Check the actual objects

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @Test
    @DisplayName("Test Artifact Store List")
    void testArtifactStoreList(VertxTestContext testContext) {
        final var queue = RabbitMQClient.create(vertx, queueConfig);
        final var client = WebClient.create(vertx, clientConfig);
        final var query = DB.datafy(
            """
            [[:crux.tx/put
              {:crux.db/id :bob.artifact-store.dev/test1
               :type       :artifact-store
               :name       "test1"
               :url        "http://localhost:8000"}]
             [:crux.tx/put
              {:crux.db/id :bob.artifact-store.dev/test2
               :type       :artifact-store
               :name       "test2"
               :url        "http://localhost:8001"}]]
            """
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/artifact-stores")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getJsonArray("message")).hasSize(0); // TODO: Check the actual objects

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));

        node.awaitTx(
            node.submitTx((List<List<?>>) query),
            Duration.ofSeconds(5)
        );

        vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, node), testContext.succeeding(id ->
            client
                .get("/artifact-stores")
                .send()
                .onSuccess(res -> testContext.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    assertThat(res.getHeader("Content-Type")).isEqualTo("application/json");
                    assertThat(res.bodyAsJsonObject().getJsonArray("message")).hasSize(2); // TODO: Check the actual objects

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow)));
    }

    @AfterEach
    void cleanup() throws SQLException {
        final var st = conn.createStatement();
        st.execute("DELETE FROM tx_events;");
        st.close();
        conn.close();
        vertx.close();
    }
}
