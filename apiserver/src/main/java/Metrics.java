/*
 * This file is part of Bob.
 *
 * Bob is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bob is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bob. If not, see <http://www.gnu.org/licenses/>.
 */

import crux.api.ICruxAPI;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rabbitmq.RabbitMQClient;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Metrics {
    static final Gauge queuedEntities = Gauge
        .build()
        .name("bob_queued_entities")
        .help("Number of queued entity changes to be applied")
        .register();
    static final Gauge queuedJobs = Gauge
        .build()
        .name("bob_queued_jobs")
        .help("Number of queued jobs to be picked up")
        .register();
    static final Gauge errors = Gauge
        .build()
        .name("bob_errors")
        .help("Number of errors")
        .register();
    static final Gauge runningJobs = Gauge
        .build()
        .name("bob_running_jobs")
        .help("Number of jobs currently running")
        .register();
    static final Gauge failedJobs = Gauge
        .build()
        .name("bob_failed_jobs")
        .help("Number of failed jobs")
        .register();
    static final Gauge passedJobs = Gauge
        .build()
        .name("bob_passed_jobs")
        .help("Number of passed jobs")
        .register();
    static final Gauge pausedJobs = Gauge
        .build()
        .name("bob_paused_jobs")
        .help("Number of paused jobs")
        .register();
    static final Gauge stoppedJobs = Gauge
        .build()
        .name("bob_stopped_jobs")
        .help("Number of stopped jobs")
        .register();

    private final static CollectorRegistry registry = CollectorRegistry.defaultRegistry;

    private static class VertxBufferedWriter extends Writer {
        private final Buffer buffer = Buffer.buffer();

        @Override
        public void write(char[] buff, int off, int len) {
            buffer.appendString(new String(buff, off, len));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        Buffer getBuffer() {
            return buffer;
        }
    }

    private static Set<String> parse(HttpServerRequest request) {
        return new HashSet<>(request.params().getAll("name[]"));
    }

    private static void countJobs(ICruxAPI node) {
        Stream.of("running", "passed", "failed", "paused", "stopped")
            .parallel()
            .map(status -> {
                final var query = DB.datafy(
                    """
                    {:find  [run]
                     :where [[run :type :pipeline-run]
                             [run :status :%s]]}
                    """.formatted(status)
                );
                return Map.of(status, node.db().query(query).size());
            })
            .forEach(metric -> {
                final var status = metric.keySet().stream().findFirst().get();
                final var count = metric.get(status);

                switch (status) {
                    case "running" -> Metrics.runningJobs.set(count);
                    case "passed" -> Metrics.passedJobs.set(count);
                    case "failed" -> Metrics.failedJobs.set(count);
                    case "paused" -> Metrics.pausedJobs.set(count);
                    case "stopped" -> Metrics.stoppedJobs.set(count);
                }
            });
    }

    public static void writeMetrics(RoutingContext routingContext, RabbitMQClient queueClient, ICruxAPI node) {
        final var writer = new VertxBufferedWriter();

        queueClient
            .queueDeclare("bob.jobs", true, false, false)
            .compose(jobs -> {
                Metrics.queuedJobs.set(jobs.getMessageCount());
                return queueClient.queueDeclare("bob.entities", true, false, false);
            })
            .compose(entities -> {
                Metrics.queuedEntities.set(entities.getMessageCount());
                return queueClient.queueDeclare("bob.errors", true, false, false);
            })
            .onSuccess(errors -> {
                Metrics.errors.set(errors.getMessageCount());

                try {
                    countJobs(node);

                    TextFormat.write004(writer, registry.filteredMetricFamilySamples(parse(routingContext.request())));
                } catch (IOException e) {
                    Handlers.toJsonResponse(routingContext, e.getMessage(), 500);
                }
                routingContext.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", TextFormat.CONTENT_TYPE_004)
                    .end(writer.getBuffer());
            })
            .onFailure(err -> Handlers.toJsonResponse(routingContext, err.getMessage(), 500));
    }
}
