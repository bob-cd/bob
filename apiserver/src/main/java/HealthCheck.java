import crux.api.ICruxAPI;
import io.vertx.rabbitmq.RabbitMQClient;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

// TODO: use better health checks for Crux

public class HealthCheck {
    private static final BiFunction<RabbitMQClient, ICruxAPI, String> queueCheck =
        (queue, _node) -> !queue.isConnected() ? "Queue is unhealthy" : null;

    private static final BiFunction<RabbitMQClient, ICruxAPI, String> dbCheck =
        (_queue, node) -> node.status() == null ? "DB is unhealthy" : null;

    public static List<String> check(RabbitMQClient queue, ICruxAPI node) {
        return List.of(queueCheck, dbCheck)
            .stream()
            .map(fn -> fn.apply(queue, node))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
