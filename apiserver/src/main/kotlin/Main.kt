import io.vertx.core.Vertx
import io.vertx.core.VertxOptions

fun main() {
    val vertx = Vertx.vertx(VertxOptions().setHAEnabled(true))
    val port = System.getenv("BOB_PORT")?.toIntOrNull() ?: 7777

    vertx.deployVerticle(APIServer("/bob/api.yaml", "0.0.0.0", port)) {
        println(
            if (it.succeeded()) "Deployed on verticle: ${it.result()}"
            else "Deployment error: ${it.cause()}"
        )
    }
}
