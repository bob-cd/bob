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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.impl.LoggerHandlerImpl;
import io.vertx.rabbitmq.RabbitMQClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public class APIServer extends AbstractVerticle {
    private final static Logger logger = LoggerFactory.getLogger(APIServer.class.getName());
    private final String apiSpec;
    private final String host;
    private final int port;
    private final RabbitMQClient queue;
    private final WebClient crux;

    public APIServer(String apiSpec, String host, int port, RabbitMQClient queue, WebClient crux) {
        this.apiSpec = apiSpec;
        this.host = host;
        this.port = port;
        this.queue = queue;
        this.crux = crux;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        this.queue
            .start()
            .compose(_it -> openAPI3RouterFrom(this.vertx, this.apiSpec))
            .compose(router -> serverFrom(this.vertx, router, this.host, this.port, this.queue, this.crux))
            .onFailure(err -> startPromise.fail(err.getCause()))
            .onSuccess(_it -> startPromise.complete());
    }

    private static Future<OpenAPI3RouterFactory> openAPI3RouterFrom(Vertx vertx, String apiSpec) {
        final Promise<OpenAPI3RouterFactory> promise = Promise.promise();

        OpenAPI3RouterFactory.create(vertx, apiSpec, promise);

        return promise.future();
    }

    private static Future<HttpServer> serverFrom(Vertx vertx, OpenAPI3RouterFactory routerFactory, String host, int port, RabbitMQClient queue, WebClient crux) {
        final var router = routerFactory
            .addHandlerByOperationId("HealthCheck", ctx -> Handlers.healthCheckHandler(ctx, queue, crux))
            .addHandlerByOperationId("PipelineCreate", ctx -> Handlers.pipelineCreateHandler(ctx, queue))
            .addFailureHandlerByOperationId("PipelineCreate", ctx -> logger.error(ctx.getBodyAsString()))
            .addHandlerByOperationId("PipelineDelete", ctx -> Handlers.pipelineDeleteHandler(ctx, queue))
            .addHandlerByOperationId("PipelineStart", ctx -> Handlers.pipelineStartHandler(ctx, queue))
            .addHandlerByOperationId("PipelineStop", ctx -> Handlers.pipelineStopHandler(ctx, queue))
            .addHandlerByOperationId("PipelineLogs", ctx -> Handlers.pipelineLogsHandler(ctx, crux))
            .addHandlerByOperationId("PipelineStatus", ctx -> Handlers.pipelineStatusHandler(ctx, crux))
            .addHandlerByOperationId("PipelineArtifactFetch", ctx -> Handlers.pipelineArtifactHandler(ctx, queue))
            .addHandlerByOperationId("PipelineList", ctx -> Handlers.pipelineListHandler(ctx, crux))
            .addHandlerByOperationId("ResourceProviderCreate", ctx -> Handlers.resourceProviderCreateHandler(ctx, queue))
            .addHandlerByOperationId("ResourceProviderDelete", ctx -> Handlers.resourceProviderDeleteHandler(ctx, queue))
            .addHandlerByOperationId("ResourceProviderList", ctx -> Handlers.resourceProviderListHandler(ctx, crux))
            .addHandlerByOperationId("ArtifactStoreCreate", ctx -> Handlers.artifactStoreCreateHandler(ctx, queue))
            .addHandlerByOperationId("ArtifactStoreDelete", ctx -> Handlers.artifactStoreDeleteHandler(ctx, queue))
            .addHandlerByOperationId("ArtifactStoreList", ctx -> Handlers.artifactStoreListHandler(ctx, crux))
            .addHandlerByOperationId("GetApiSpec", Handlers::apiSpecHandler)
            .addGlobalHandler(LoggerHandler.create())
            .getRouter();

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, host)
            .onSuccess(_it -> logger.info(format("Bob is listening on port %d", port)))
            .onFailure(err -> logger.error(err.getCause().toString()));
    }
}
