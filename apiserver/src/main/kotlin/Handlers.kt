
import com.rabbitmq.client.AMQP
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.rabbitmq.RabbitMQClient
import org.tinylog.Logger

fun toJsonResponse(routingContext: RoutingContext, content: Any): Future<Void> =
    routingContext.response()
        .putHeader("content-type", "application/json")
        .end(JsonObject(mapOf("message" to content)).encode())

fun publishToEntities(queue: RabbitMQClient, type: String, payload: JsonObject) {
    queue.basicPublish(
        "",
        "entities",
        AMQP.BasicProperties.Builder().type(type).build(),
        payload.toBuffer()
    ) {
        if (it.succeeded()) Logger.info{"Published message on entities: ${it.result()}"}
        else Logger.error{"Error publishing message on entities: ${it.cause().printStackTrace()}"}
    }
}

fun healthCheckHandler(routingContext: RoutingContext) =
    toJsonResponse(routingContext, "Yes we can! \uD83D\uDD28 \uD83D\uDD28")

fun pipelineCreateHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val pipeline = routingContext.bodyAsJson
    // TODO make JsonObject from params directly?!?
    val payload = pipeline.put("name", name).put("group", group)

    publishToEntities(queue, "pipeline/create", payload)

    return toJsonResponse(routingContext, "Successfully Created Pipeline $group $name")
}

fun pipelineDeleteHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val payload = JsonObject().put("name", name).put("group", group)

    publishToEntities(queue, "pipeline/delete", payload)

    return toJsonResponse(routingContext, "Successfully Deleted Pipeline $group $name")
}

fun pipelineStartHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    // TODO make JsonObject from params directly?!?
    val payload = JsonObject().put("name", name).put("group", group)

    publishToEntities(queue, "pipeline/start", payload)

    return toJsonResponse(routingContext, "Successfully Started Pipeline $group $name")
}

fun pipelineStopHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]
    val payload = JsonObject().put("name", name).put("group", group).put("number", number)

    publishToEntities(queue, "pipeline/start", payload)

    return toJsonResponse(routingContext, "Successfully Stopped Pipeline $group $name $number")
}

fun pipelineLogsHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]
    val offset = params["offset"]
    val lines = params["lines"]

    Logger.info{group}
    Logger.info{name}
    Logger.info{number}
    Logger.info{offset}
    Logger.info{lines}
    // TODO DB interaction

    return toJsonResponse(routingContext, "Logs for Pipeline $group $name $number with Offset $offset and Lines $lines")
}

fun pipelineStatusHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]

    Logger.info{group}
    Logger.info{name}
    Logger.info{number}
    // TODO DB interaction

    return toJsonResponse(routingContext, "Status for Pipeline $group $name $number")
}

fun pipelineArtifactHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    routingContext.response()
        .sendFile("test.tar.gz")
    // TODO DB interaction and returning file via queue?

    return toJsonResponse(routingContext, "Sending File completed!")
}

fun pipelineListHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val status = params["status"]

    Logger.info{group}
    Logger.info{name}
    Logger.info{status}
    // TODO DB interaction

    return toJsonResponse(routingContext, "Listing Pipelines for $group $name $status")
}

fun apiSpecHandler(routingContext: RoutingContext): Future<Void> =
    routingContext.response()
        .putHeader("content-type", "application/yaml")
        .end({}.javaClass.getResource("bob/api.yaml").readText())

