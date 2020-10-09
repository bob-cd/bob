import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import crux.api.ICruxAPI;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.rabbitmq.RabbitMQClient;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO: use better health checks for Crux
// TODO: Does this have to be THIS complex??

record HealthContext(RabbitMQClient queue, ICruxAPI node, Vertx vertx) {
}

// See: https://stackoverflow.com/questions/41467014/vert-x-java-listfutures-parameterization
interface GenericCompositeFuture extends CompositeFuture {

    static <T> CompositeFuture all(List<Future<T>> futures) {
        return CompositeFutureImpl.all(futures.toArray(new Future[0]));
    }
}

public class HealthCheck {
    private static final Function<HealthContext, Future<String>> queueCheck = context ->
        context.queue().isConnected() ?
            Future.succeededFuture() :
            Future.failedFuture("Queue is unhealthy");

    private static final Function<HealthContext, Future<String>> dbCheck = context ->
        context.node().status() != null ?
            Future.succeededFuture() :
            Future.failedFuture("DB is unhealthy");

    private static Future<String> externalSystemCheck(HealthContext context, String system) {
        try {
            final Promise<String> promise = Promise.promise();
            final var query = DB.datafy(
                """
                {:find  [(eql/project system [:url])]
                 :where [[system :type :%s]]}
                """.formatted(system)
            );
            final var key = Keyword.intern(Symbol.create("url"));
            final var httpClient = WebClient.create(context.vertx());
            final var healthChecks = context
                .node()
                .db()
                .query(query)
                .stream()
                .parallel()
                .map(it -> it.get(0))
                .map(it -> (String) ((PersistentArrayMap) it).get(key))
                .map(it -> it + "/ping")
                .map(httpClient::getAbs)
                .map(HttpRequest::send)
                .collect(Collectors.toList());

            GenericCompositeFuture
                .all(healthChecks)
                .onSuccess(checks -> {
                    final var failures = checks
                        .list()
                        .stream()
                        .map(it -> (HttpResponse<Buffer>) it)
                        .filter(it -> it.statusCode() >= 400)
                        .map(HttpResponse::bodyAsString)
                        .collect(Collectors.toList());

                    if (failures.isEmpty())
                        promise.complete();
                    else
                        promise.fail(failures.toString());
                });

            return promise.future();
        } catch (Exception e) {
            return Future.failedFuture("External system(s) %s unhealthy: %s".formatted(system, e.getMessage()));
        }
    }

    private static final Function<HealthContext, Future<String>> artifactStoresCheck = context ->
        externalSystemCheck(context, "artifact-store");

    private static final Function<HealthContext, Future<String>> resourceProviderCheck = context ->
        externalSystemCheck(context, "resource-provider");

    public static Future<String> check(RabbitMQClient queue, ICruxAPI node, Vertx vertx) {
        final var ctx = new HealthContext(queue, node, vertx);
        final Promise<String> promise = Promise.promise();

        final var checks = List.of(queueCheck, dbCheck, artifactStoresCheck, resourceProviderCheck)
            .stream()
            .map(fn -> fn.apply(ctx))
            .collect(Collectors.toList());

        GenericCompositeFuture
            .all(checks)
            .onSuccess(_it -> promise.complete())
            .onFailure(it -> promise.fail(it.getCause()));

        return promise.future();
    }
}
