import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.buffer.Buffer
import io.vertx.rabbitmq.RabbitMQClient
import io.vertx.rabbitmq.RabbitMQOptions
import java.io.File

sealed class Maybe<out T>

data class Just<T>(val value: T) : Maybe<T>()

object None : Maybe<Nothing>() {
    override fun toString() = "None"
}

fun <T, R> bind(m: Maybe<T>, f: (T) -> Maybe<R>) = when (m) {
    is Just<T> -> f(m.value)
    else -> None
}

fun <T> unit(v: T) = Just(v)

fun <T> chain(init: Maybe<T>, vararg effects: (T) -> Maybe<T>) = effects.fold(init, ::bind)

fun publish(client: RabbitMQClient, message: Buffer) = try {
    Just(client.basicPublish("", "entities", message))
} catch (e: Exception) {
    None
}

fun main() {
    System.getProperties()["io.netty.tryReflectionSetAccessible"] = "true";
    val vertx = Vertx.vertx(VertxOptions().setHAEnabled(true))
    val port = System.getenv("BOB_PORT")?.toIntOrNull() ?: 7777

    vertx.deployVerticle(APIServer("/bob/api.yaml", "0.0.0.0", port)) {
        println(
            if (it.succeeded()) "Deployed on verticle: ${it.result()}"
            else "Deployment error: ${it.cause()}"
        )
    }
    val config = RabbitMQOptions()
    val client = RabbitMQClient.create(vertx, config)
    val connected = client.start()

    Thread.sleep(1000)

    val message = Buffer.buffer(File("./createPipeline.message.json").readText(Charsets.UTF_8))

    val result = chain(
        unit(publish(client, message))
    )

    println("Connected: $connected - Result: $result")
}
