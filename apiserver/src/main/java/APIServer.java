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
import io.vertx.core.Vertx;
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
            .compose(_it -> queue.exchangeDeclare("bob.direct", "direct", false, false))
            .compose(_it -> queue.exchangeDeclare("bob.fanout", "fanout", false, false))
            .compose(_it -> queue.queueDeclare("bob.entities", true, false, false))
            .compose(_it -> queue.queueDeclare("bob.jobs", true, false, false))
            .compose(_it -> queue.queueDeclare("bob.errors", true, false, false))
            .compose(_it -> queue.queueBind("bob.jobs", "bob.direct", "bob.jobs"))
            .compose(_it -> queue.queueBind("bob.jobs", "bob.fanout", "bob.jobs"))
            .compose(_it -> queue.queueBind("bob.entities", "bob.direct", "bob.entities"))
            .compose(_it -> openAPI3RouterFrom(this.vertx, this.apiSpec))
            .compose(router -> serverFrom(this.vertx, router, this.host, this.port, this.queue, this.node))
            .onFailure(err -> startPromise.fail(err.getCause()))
            .onSuccess(_it -> startPromise.complete());
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws IOException {
        this.node.close();
        this.queue.stop(_it -> stopPromise.complete());
    }

    private static Future<OpenAPI3RouterFactory> openAPI3RouterFrom(Vertx vertx, String apiSpec) {
        final Promise<OpenAPI3RouterFactory> promise = Promise.promise();

        OpenAPI3RouterFactory.create(vertx, apiSpec, promise);

        return promise.future();
    }

    private static Future<HttpServer> serverFrom(
        Vertx vertx, OpenAPI3RouterFactory routerFactory, String host, int port, RabbitMQClient queue, ICruxAPI node
    ) {
        final var router = routerFactory
            .addHandlerByOperationId("GetApiSpec", Handlers::apiSpecHandler)
            .addHandlerByOperationId("HealthCheck", ctx -> Handlers.healthCheckHandler(ctx, queue, node))
            .addHandlerByOperationId("PipelineCreate", ctx -> Handlers.pipelineCreateHandler(ctx, queue))
            .addGlobalHandler(LoggerHandler.create())
            .getRouter();

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, host)
            .onSuccess(_it -> logger.info("Bob is listening on port " + port))
            .onFailure(err -> logger.error(err.getCause().toString()));
    }
}
