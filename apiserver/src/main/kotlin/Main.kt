import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.rabbitmq.RabbitMQClient
import io.vertx.rabbitmq.RabbitMQOptions
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("bob.apiserver")

fun main() {
    val vertx = Vertx.vertx(VertxOptions().setHAEnabled(true))
    ConfigRetriever.create(vertx).getConfig {
        val config = it.result()

        val rmqConfig = config.getJsonObject("rabbitmq")
        val cruxConfig = config.getJsonObject("crux")
        val httpConfig = config.getJsonObject("http")

        val apiSpec = httpConfig.getString("apiSpec", "/bob/api.yaml")
        val httpHost = httpConfig.getString("host", "localhost")
        val httpPort = httpConfig.getInteger("port", 7777)

        logger.info(config.toString())

        val queue : RabbitMQClient = RabbitMQClient.create(vertx, RabbitMQOptions(rmqConfig))
        val client = WebClient.create(vertx, WebClientOptions(cruxConfig))

        vertx.deployVerticle(APIServer(apiSpec, httpHost, httpPort, queue, client)) {
            if (it.succeeded()) logger.info("Deployed on verticle: ${it.result()}")
            else logger.error("Deployment error: ${it.cause()}")
        }
    }
}
