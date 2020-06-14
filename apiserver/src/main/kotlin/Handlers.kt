
import com.rabbitmq.client.AMQP
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.rabbitmq.RabbitMQClient

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
        if (it.succeeded()) logger.debug("Published message on entities!")
        else logger.error("Error publishing message on entities: ${it.cause().printStackTrace()}")
    }
}

fun healthCheckHandler(routingContext: RoutingContext, queue: RabbitMQClient, client: WebClient) {
    // TODO maybe implement with proper healthcheck
    val checkDB = client.get(7778, "localhost", "/").send() {
        if (it.failed()) {
            logger.error("Healthcheck failed for CruxDB!")
            routingContext.fail(it.cause())
        } else {
            logger.debug("Healthcheck succeeded for CruxDB!")
        }
    }
    val checkRMQ = client.get(15672, "localhost", "/").send() {
        if (it.failed()) {
            logger.error("Healthcheck failed for CruxDB!")
            routingContext.fail(it.cause())
        } else {
            logger.debug("Healthcheck succeeded for RabbitMQ!")
        }
    }
    toJsonResponse(routingContext, "Yes we can! \uD83D\uDD28 \uD83D\uDD28")
}

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

fun pipelineLogsHandler(routingContext: RoutingContext, client: WebClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]
    val offset = params["offset"]
    val lines = params["lines"]

    logger.info(group)
    logger.info(name)
    logger.info(number)
    logger.info(offset)
    logger.info(lines)
    // TODO DB interaction

    return toJsonResponse(routingContext, "Logs for Pipeline $group $name $number with Offset $offset and Lines $lines")
}

fun pipelineStatusHandler(routingContext: RoutingContext, client: WebClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val number = params["number"]

    logger.info(group)
    logger.info(name)
    logger.info(number)
    // TODO DB interaction

    return toJsonResponse(routingContext, "Status for Pipeline $group $name $number")
}

fun pipelineArtifactHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    routingContext.response()
        .sendFile("test.tar.gz")
    // TODO DB interaction and returning file via queue?

    return toJsonResponse(routingContext, "Sending File completed!")
}

fun pipelineListHandler(routingContext: RoutingContext, client: WebClient): Future<Void> {
    val params = routingContext.request().params()
    val group = params["group"]
    val name = params["name"]
    val status = params["status"]

    logger.info(group)
    logger.info(name)
    logger.info(status)
    // TODO DB interaction

    return toJsonResponse(routingContext, "Listing Pipelines for $group $name $status")
}

fun resourceProviderCreateHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val name = routingContext.request().params()["name"]
    val payload = routingContext.bodyAsJson.put("name", name)

    logger.info("Creating Resource Provider with $payload")

    publishToEntities(queue, "resource-provider/create", payload)

    return toJsonResponse(routingContext, "Created Resource Provider $payload")
}

fun resourceProviderDeleteHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val payload = JsonObject().put("name", routingContext.request().params()["name"])

    logger.info("Deleting Resource Provider with $payload")

    publishToEntities(queue, "resource-provider/create", payload)

    return toJsonResponse(routingContext, "Deleted Resource Provider $payload")
}

fun resourceProviderListHandler(routingContext: RoutingContext, client: WebClient): Future<Void> {
    return toJsonResponse(routingContext, client.get(7778, "localhost", "/"))
}

fun artifactStoreCreateHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val name = routingContext.request().params()["name"]
    val payload = routingContext.bodyAsJson.put("name", name)

    logger.info("Creating Artifact Store with $payload")

    publishToEntities(queue, "artifact-store/create", payload)

    return toJsonResponse(routingContext, "Created Artifact Store $name")
}

fun artifactStoreDeleteHandler(routingContext: RoutingContext, queue: RabbitMQClient): Future<Void> {
    val payload = JsonObject().put("name", routingContext.request().params()["name"])

    logger.info("Deleting Artifact Store with $payload")

    publishToEntities(queue, "artifact-store/delete", payload)

    return toJsonResponse(routingContext, "Deleted Artifact Store $payload")
}

fun artifactStoreListHandler(routingContext: RoutingContext, client: WebClient): Future<Void> {
    return toJsonResponse(routingContext, client.get(7778, "localhost", "/"))
}

fun apiSpecHandler(routingContext: RoutingContext): Future<Void> =
    routingContext.response()
        .putHeader("content-type", "application/yaml")
        .end({}.javaClass.getResource("bob/api.yaml").readText())

