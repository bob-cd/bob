import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.rabbitmq.RabbitMQClient
import io.vertx.rabbitmq.RabbitMQOptions
import org.tinylog.Logger

fun main() {
    System.getProperties()["io.netty.tryReflectionSetAccessible"] = "true";
    val vertx = Vertx.vertx(VertxOptions().setHAEnabled(true))
    val port = System.getenv("BOB_PORT")?.toIntOrNull() ?: 7777

    val queue : RabbitMQClient = RabbitMQClient.create(vertx, RabbitMQOptions())

    vertx.deployVerticle(APIServer("/bob/api.yaml", "0.0.0.0", port, queue)) {
        Logger.info {
            if (it.succeeded()) "Deployed on verticle: ${it.result()}"
            else "Deployment error: ${it.cause()}"
        }
    }
}
