import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.ext.web.client.WebClient
import io.vertx.rabbitmq.RabbitMQClient

fun openAPI3RouterFrom(vertx: Vertx, apiSpec: String): Future<OpenAPI3RouterFactory> {
    val promise = Promise.promise<OpenAPI3RouterFactory>()

    OpenAPI3RouterFactory.create(vertx, apiSpec, promise)

    return promise.future()
}

fun serverFrom(vertx: Vertx, routerFactory: OpenAPI3RouterFactory, host: String, port: Int, queue: RabbitMQClient, client: WebClient): Future<HttpServer> {
    val router =
        routerFactory.addHandlerByOperationId("HealthCheck") {
            healthCheckHandler(it, queue, client)
        }.addHandlerByOperationId("PipelineCreate") {
            pipelineCreateHandler(it, queue)
        }.addHandlerByOperationId("PipelineDelete") {
            pipelineDeleteHandler(it, queue)
        }.addHandlerByOperationId("PipelineStart") {
            pipelineStartHandler(it, queue)
        }.addHandlerByOperationId("PipelineStop") {
            pipelineStopHandler(it, queue)
        }.addHandlerByOperationId("PipelineLogs") {
            pipelineLogsHandler(it, client)
        }.addHandlerByOperationId("PipelineStatus") {
            pipelineStatusHandler(it, client)
        }.addHandlerByOperationId("PipelineArtifactFetch") {
            pipelineArtifactHandler(it, queue)
        }.addHandlerByOperationId("PipelineList") {
            pipelineListHandler(it, client)
        }.addHandlerByOperationId("ResourceProviderCreate") {
            resourceProviderCreateHandler(it, queue)
        }.addHandlerByOperationId("ResourceProviderDelete") {
            resourceProviderDeleteHandler(it, queue)
        }.addHandlerByOperationId("ResourceProviderList") {
            resourceProviderListHandler(it, client)
        }.addHandlerByOperationId("ArtifactStoreCreate") {
            artifactStoreCreateHandler(it, queue)
        }.addHandlerByOperationId("ArtifactStoreDelete") {
            artifactStoreDeleteHandler(it, queue)
        }.addHandlerByOperationId("ArtifactStoreList") {
            artifactStoreListHandler(it, client)
        }.addHandlerByOperationId("GetApiSpec") {
            apiSpecHandler(it)
        }.router

    return vertx.createHttpServer()
        .requestHandler(router)
        .listen(port, host)
        .onSuccess {
            logger.info("Bob's listening on port: $port")
        }.onFailure {
            logger.error(it.cause.toString())
        }
}

class APIServer(private val apiSpec: String,
                private val host: String,
                private val port: Int,
                private val queue: RabbitMQClient,
                private val client: WebClient) : AbstractVerticle() {
    override fun start(startPromise: Promise<Void>) {
        queue.start().compose {
            openAPI3RouterFrom(this.vertx, this.apiSpec)
        }.compose {
            serverFrom(this.vertx, it, this.host, this.port, this.queue, this.client)
        }.onFailure {
            startPromise.fail(it.cause)
        }.onSuccess {
            startPromise.complete()
        }
    }
}
