import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.rabbitmq.RabbitMQClient
import io.vertx.rabbitmq.RabbitMQOptions

fun toJsonResponse(routingContext: RoutingContext, content: Any): Future<Void> =
    routingContext.response()
        .putHeader("content-type", "application/json")
        .end(JsonObject(mapOf("message" to content)).encode())

fun healthCheckHandler(routingContext: RoutingContext) =
    toJsonResponse(routingContext, "Yes we can! \uD83D\uDD28 \uD83D\uDD28")

fun pipelineCreateHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val pipeline = routingContext.bodyAsJson

    println(group)
    println(name)
    println(pipeline)

    return toJsonResponse(routingContext, pipeline)
}

fun pipelineDeleteHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]

    println(group)
    println(name)

    return toJsonResponse(routingContext, "Successfully Deleted Pipeline $group $name")
}

fun pipelineStartHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]

    println(group)
    println(name)

    return toJsonResponse(routingContext, "Successfully Started Pipeline $group $name")
}

fun pipelineStopHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]

    println(group)
    println(name)
    println(number)

    return toJsonResponse(routingContext, "Successfully Stopped Pipeline $group $name $number")
}

fun pipelineLogsHandler(routingContext: RoutingContext): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]
    val offset = params["offset"]
    val lines = params["lines"]

    println(group)
    println(name)
    println(number)
    println(offset)
    println(lines)

    return toJsonResponse(routingContext, "Logs for Pipeline $group $name $number with Offset $offset and Lines $lines")
}

fun apiSpecHandler(routingContext: RoutingContext): Future<Void> =
    routingContext.response()
        .putHeader("content-type", "application/yaml")
        .end({}.javaClass.getResource("bob/api.yaml").readText())

fun pipelineArtifactHandler(routingContext: RoutingContext): Future<Void> =
    routingContext.response()
        .sendFile("test.tar.gz")
