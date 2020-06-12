import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.rabbitmq.RabbitMQClient
import org.tinylog.Logger

fun openAPI3RouterFrom(vertx: Vertx, apiSpec: String): Future<OpenAPI3RouterFactory> {
    val promise = Promise.promise<OpenAPI3RouterFactory>()

    OpenAPI3RouterFactory.create(vertx, apiSpec, promise)

    return promise.future()
}

fun serverFrom(vertx: Vertx, routerFactory: OpenAPI3RouterFactory, host: String, port: Int, queue: RabbitMQClient): Future<HttpServer> {
    val router =
        routerFactory.addHandlerByOperationId("HealthCheck") {
            healthCheckHandler(it)
        }.addHandlerByOperationId("PipelineCreate") {
            pipelineCreateHandler(it, queue)
        }.addHandlerByOperationId("PipelineDelete") {
            pipelineDeleteHandler(it, queue)
        }.addHandlerByOperationId("PipelineStart") {
            pipelineStartHandler(it, queue)
        }.addHandlerByOperationId("PipelineStop") {
            pipelineStopHandler(it, queue)
        }.addHandlerByOperationId("PipelineLogs") {
            pipelineLogsHandler(it)
        }.addHandlerByOperationId("GetApiSpec") {
            apiSpecHandler(it)
        }.addHandlerByOperationId("PipelineArtifactFetch") {
            pipelineArtifactHandler(it, queue)
        }.router

    return vertx.createHttpServer()
        .requestHandler(router)
        .listen(port, host)
        .onSuccess {
            Logger.info{"Bob's listening on port: $port"}
        }.onFailure {
            Logger.error{it.cause}
        }
}

class APIServer(private val apiSpec: String, private val host: String, private val port: Int, private val queue: RabbitMQClient) : AbstractVerticle() {
    override fun start(startPromise: Promise<Void>) {
        queue.start().compose {
            openAPI3RouterFrom(this.vertx, this.apiSpec)
        }.compose {
            serverFrom(this.vertx, it, this.host, this.port, this.queue)
        }.onFailure {
            startPromise.fail(it.cause)
        }.onSuccess {
            startPromise.complete()
        }
    }
}
