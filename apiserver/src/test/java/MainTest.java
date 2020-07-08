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
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
@DisplayName("👋 A fairly basic test example")
@ExtendWith(VertxExtension.class)
class APIServerTest {

    @Test
    @DisplayName("⏱ Count 3 timer ticks")
    void countThreeTicks(Vertx vertx, VertxTestContext testContext) {
        AtomicInteger counter = new AtomicInteger();
        vertx.setPeriodic(100, id -> {
            if (counter.incrementAndGet() == 3) {
                testContext.completeNow();
            }
        });
    }

    @Test
    @DisplayName("⏱ Count 3 timer ticks, with a checkpoint")
    void countThreeTicksWithCheckpoints(Vertx vertx, VertxTestContext testContext) {
        Checkpoint checkpoint = testContext.checkpoint(3);
        vertx.setPeriodic(100, id -> checkpoint.flag());
    }

    @Test
    @DisplayName("🚀 Deploy a HTTP service verticle and make 10 requests")
    void useAPIServer(Vertx vertx, VertxTestContext testContext) {
        Checkpoint deploymentCheckpoint = testContext.checkpoint();
        Checkpoint requestCheckpoint = testContext.checkpoint(10);
        ConfigRetriever.create(vertx).getConfig(config -> {
            if (config.succeeded()) {
                final var conf = config.result();
                final var rmqConfig = conf.getJsonObject("rabbitmq");
                final var cruxConfig = conf.getJsonObject("crux");
                final var httpConfig = conf.getJsonObject("http");

                final var apiSpec = httpConfig.getString("apiSpec", "/bob/api.yaml");
                final var httpHost = httpConfig.getString("host", "localhost");
                final var httpPort = httpConfig.getInteger("port", 7777);

                final var queue = RabbitMQClient.create(vertx, new RabbitMQOptions(rmqConfig));
                final var client = WebClient.create(vertx, new WebClientOptions(cruxConfig));

                vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, client), testContext.succeeding(id -> {
                    deploymentCheckpoint.flag();
                    for (int i = 0; i < 10; i++) {
                        client.get(11981, "localhost", "/")
                                .as(BodyCodec.string())
                                .send(testContext.succeeding(resp -> {
                                    testContext.verify(() -> {
                                        assertThat(resp.statusCode()).isEqualTo(200);
                                        assertThat(resp.body()).contains("Yo!");
                                        requestCheckpoint.flag();
                                    });
                                }));
                    }
                }));
            }
        });
    }

    @DisplayName("➡️ A nested test with customized lifecycle")
    @Nested
    class CustomLifecycleTest {

        Vertx vertx;

        @BeforeEach
        void prepare() {
            vertx = Vertx.vertx(new VertxOptions()
                    .setMaxEventLoopExecuteTime(1000)
                    .setPreferNativeTransport(true));
        }

        @Test
        @DisplayName("⬆️ Deploy APIServer")
        void deployAPIServer(VertxTestContext testContext) {
            ConfigRetriever.create(vertx).getConfig(config -> {
                if (config.succeeded()) {
                    final var conf = config.result();
                    final var rmqConfig = conf.getJsonObject("rabbitmq");
                    final var cruxConfig = conf.getJsonObject("crux");
                    final var httpConfig = conf.getJsonObject("http");

                    final var apiSpec = httpConfig.getString("apiSpec", "/bob/api.yaml");
                    final var httpHost = httpConfig.getString("host", "localhost");
                    final var httpPort = httpConfig.getInteger("port", 7777);

                    final var queue = RabbitMQClient.create(vertx, new RabbitMQOptions(rmqConfig));
                    final var client = WebClient.create(vertx, new WebClientOptions(cruxConfig));

                    vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, client), testContext.succeeding(id ->
                            testContext.completeNow()));
                }
            });
        }

        @Test
        @DisplayName("🛂 Make a HTTP client request to APIServer")
        void httpRequest(VertxTestContext testContext) {
            ConfigRetriever.create(vertx).getConfig(config -> {
                if (config.succeeded()) {
                    final var conf = config.result();
                    final var rmqConfig = conf.getJsonObject("rabbitmq");
                    final var cruxConfig = conf.getJsonObject("crux");
                    final var httpConfig = conf.getJsonObject("http");

                    final var apiSpec = httpConfig.getString("apiSpec", "/bob/api.yaml");
                    final var httpHost = httpConfig.getString("host", "localhost");
                    final var httpPort = httpConfig.getInteger("port", 7777);

                    final var queue = RabbitMQClient.create(vertx, new RabbitMQOptions(rmqConfig));
                    final var client = WebClient.create(vertx, new WebClientOptions(cruxConfig));

                    vertx.deployVerticle(new APIServer(apiSpec, httpHost, httpPort, queue, client), testContext.succeeding(id ->
                            testContext.completeNow()));
                    client.get(11981, "localhost", "/yo")
                            .as(BodyCodec.string())
                            .send(testContext.succeeding(resp -> {
                                testContext.verify(() -> {
                                    assertThat(resp.statusCode()).isEqualTo(200);
                                    assertThat(resp.body()).contains("Yo!");
                                    testContext.completeNow();
                                });
                            }));
                }
            });
        }

        @AfterEach
        void cleanup() {
            vertx.close();
        }
    }
}