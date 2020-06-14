import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.ext.web.client.WebClient
import io.vertx.rabbitmq.RabbitMQClient
import io.vertx.rabbitmq.RabbitMQOptions
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("bob.apiserver")


fun main() {
    val vertx = Vertx.vertx(VertxOptions().setHAEnabled(true))
    val port = System.getenv("BOB_PORT")?.toIntOrNull() ?: 7777

    val queue : RabbitMQClient = RabbitMQClient.create(vertx, RabbitMQOptions())

    val client = WebClient.create(vertx)

    vertx.deployVerticle(APIServer("/bob/api.yaml", "0.0.0.0", port, queue, client)) {
        if (it.succeeded()) logger.info("Deployed on verticle: ${it.result()}")
        else logger.error("Deployment error: ${it.cause()}")
    }
}
