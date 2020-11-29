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
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.rabbitmq.RabbitMQClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class APIServer extends AbstractVerticle {
    private final static Logger logger = LoggerFactory.getLogger(APIServer.class.getName());
    private final String apiSpec, host;
    private final int port, healthCheckFreq;
    private final RabbitMQClient queue;
    private final ICruxAPI node;

    public APIServer(String apiSpec, String host, int port, RabbitMQClient queue, ICruxAPI node, int healthCheckFreq) {
        this.apiSpec = apiSpec;
        this.host = host;
        this.port = port;
        this.queue = queue;
        this.node = node;
        this.healthCheckFreq = healthCheckFreq;
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
            .compose(_it -> RouterBuilder.create(this.vertx, this.apiSpec))
            .compose(this::makeServer)
            .onFailure(err -> startPromise.fail(err.getCause()))
            .onSuccess(_it -> startPromise.complete());
    }

    public void stop(Promise<Void> stopPromise) throws IOException {
        this.node.close();
        this.queue.stop(_it -> stopPromise.complete());
    }

    private Future<HttpServer> makeServer(RouterBuilder routerBuilder) {
        routerBuilder.rootHandler(LoggerHandler.create());
        routerBuilder.operation("GetApiSpec").handler(ctx -> Handlers.apiSpecHandler(ctx, this.vertx));
        routerBuilder.operation("HealthCheck").handler(ctx -> Handlers.healthCheckHandler(ctx, this.queue, this.node, this.vertx));
        routerBuilder.operation("PipelineCreate").handler(ctx -> Handlers.pipelineCreateHandler(ctx, this.queue));
        routerBuilder.operation("PipelineDelete").handler(ctx -> Handlers.pipelineDeleteHandler(ctx, this.queue));
        routerBuilder.operation("PipelineStart").handler(ctx -> Handlers.pipelineStartHandler(ctx, this.queue));
        routerBuilder.operation("PipelineStop").handler(ctx -> Handlers.pipelineStopHandler(ctx, this.queue));
        routerBuilder.operation("PipelinePause").handler(ctx -> Handlers.pipelinePauseUnpauseHandler(ctx, this.queue, true));
        routerBuilder.operation("PipelineUnpause").handler(ctx -> Handlers.pipelinePauseUnpauseHandler(ctx, this.queue, false));
        routerBuilder.operation("PipelineLogs").handler(ctx -> Handlers.pipelineLogsHandler(ctx, this.node));
        routerBuilder.operation("PipelineStatus").handler(ctx -> Handlers.pipelineStatusHandler(ctx, this.node));
        routerBuilder.operation("PipelineArtifactFetch").handler(ctx -> Handlers.pipelineArtifactHandler(ctx, this.node, this.vertx));
        routerBuilder.operation("PipelineList").handler(ctx -> Handlers.pipelineListHandler(ctx, this.node));
        routerBuilder.operation("ResourceProviderCreate").handler(ctx -> Handlers.resourceProviderCreateHandler(ctx, this.queue));
        routerBuilder.operation("ResourceProviderDelete").handler(ctx -> Handlers.resourceProviderDeleteHandler(ctx, this.queue));
        routerBuilder.operation("ResourceProviderList").handler(ctx -> Handlers.resourceProviderListHandler(ctx, this.node));
        routerBuilder.operation("ArtifactStoreCreate").handler(ctx -> Handlers.artifactStoreCreateHandler(ctx, this.queue));
        routerBuilder.operation("ArtifactStoreDelete").handler(ctx -> Handlers.artifactStoreDeleteHandler(ctx, this.queue));
        routerBuilder.operation("ArtifactStoreList").handler(ctx -> Handlers.artifactStoreListHandler(ctx, this.node));
        routerBuilder.operation("Query").handler(ctx -> Handlers.queryHandler(ctx, this.node));
        routerBuilder.operation("GetError").handler(ctx -> Handlers.errorsHandler(ctx, this.queue));

        this.vertx.setPeriodic(this.healthCheckFreq, _it ->
            HealthCheck
                .check(queue, node, vertx)
                .onFailure(err -> logger.error("Health checks failing: " + err.getMessage()))
        );

        return this.vertx.createHttpServer()
            .requestHandler(routerBuilder.createRouter())
            .listen(this.port, this.host)
            .onSuccess(_it -> logger.info("Bob is listening on port " + this.port))
            .onFailure(err -> logger.error(err.getMessage()));
    }
}
