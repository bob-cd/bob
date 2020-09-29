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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.rabbitmq.RabbitMQClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class APIServer extends AbstractVerticle {
    private final static Logger logger = LoggerFactory.getLogger(APIServer.class.getName());
    private final String apiSpec;
    private final String host;
    private final int port;
    private final RabbitMQClient queue;
    private final ICruxAPI node;

    public APIServer(String apiSpec, String host, int port, RabbitMQClient queue, ICruxAPI node) {
        this.apiSpec = apiSpec;
        this.host = host;
        this.port = port;
        this.queue = queue;
        this.node = node;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        this.queue
            .start()
            .compose(_it -> queue.exchangeDeclare("bob.direct", "direct", true, false))
            .compose(_it -> queue.exchangeDeclare("bob.fanout", "fanout", true, false))
            .compose(_it -> queue.queueDeclare("bob.entities", true, false, false))
            .compose(_it -> queue.queueDeclare("bob.jobs", true, false, false))
            .compose(_it -> queue.queueDeclare("bob.errors", true, false, false))
            .compose(_it -> queue.queueBind("bob.jobs", "bob.direct", "bob.jobs"))
            .compose(_it -> queue.queueBind("bob.entities", "bob.direct", "bob.entities"))
            .compose(_it -> makeRouter())
            .compose(this::makeServer)
            .onFailure(err -> startPromise.fail(err.getCause()))
            .onSuccess(_it -> startPromise.complete());
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws IOException {
        this.node.close();
        this.queue.stop(_it -> stopPromise.complete());
    }

    private Future<OpenAPI3RouterFactory> makeRouter() {
        final Promise<OpenAPI3RouterFactory> promise = Promise.promise();

        OpenAPI3RouterFactory.create(this.vertx, this.apiSpec, promise);

        return promise.future();
    }

    private Future<HttpServer> makeServer(OpenAPI3RouterFactory routerFactory) {
        final var router = routerFactory
            .addHandlerByOperationId("GetApiSpec", Handlers::apiSpecHandler)
            .addHandlerByOperationId("HealthCheck", ctx -> Handlers.healthCheckHandler(ctx, this.queue, this.node))
            .addHandlerByOperationId("PipelineCreate", ctx -> Handlers.pipelineCreateHandler(ctx, this.queue))
            .addHandlerByOperationId("PipelineDelete", ctx -> Handlers.pipelineDeleteHandler(ctx, this.queue))
            .addHandlerByOperationId("PipelineStart", ctx -> Handlers.pipelineStartHandler(ctx, this.queue))
            .addHandlerByOperationId("PipelineStop", ctx -> Handlers.pipelineStopHandler(ctx, this.queue))
            .addHandlerByOperationId("PipelineLogs", ctx -> Handlers.pipelineLogsHandler(ctx, this.node))
            .addHandlerByOperationId("PipelineStatus", ctx -> Handlers.pipelineStatusHandler(ctx, this.node))
            .addHandlerByOperationId("PipelineArtifactFetch", ctx -> Handlers.pipelineArtifactHandler(ctx, this.node, this.vertx))
            .addHandlerByOperationId("PipelineList", ctx -> Handlers.pipelineListHandler(ctx, this.node))
            .addHandlerByOperationId("ResourceProviderCreate", ctx -> Handlers.resourceProviderCreateHandler(ctx, this.queue))
            .addHandlerByOperationId("ResourceProviderDelete", ctx -> Handlers.resourceProviderDeleteHandler(ctx, this.queue))
            .addHandlerByOperationId("ResourceProviderList", ctx -> Handlers.resourceProviderListHandler(ctx, this.node))
            .addHandlerByOperationId("ArtifactStoreCreate", ctx -> Handlers.artifactStoreCreateHandler(ctx, this.queue))
            .addHandlerByOperationId("ArtifactStoreDelete", ctx -> Handlers.artifactStoreDeleteHandler(ctx, this.queue))
            .addHandlerByOperationId("ArtifactStoreList", ctx -> Handlers.artifactStoreListHandler(ctx, this.node))
            .addGlobalHandler(LoggerHandler.create())
            .getRouter();

        this.vertx.setPeriodic(5000, _it -> {
            final var checks = HealthCheck.check(queue, node);

            if (!checks.isEmpty())
                logger.error("Health checks failing: " + checks);
        });

        return this.vertx.createHttpServer()
            .requestHandler(router)
            .listen(this.port, this.host)
            .onSuccess(_it -> logger.info("Bob is listening on port " + this.port))
            .onFailure(err -> logger.error(err.getMessage()));
    }
}
