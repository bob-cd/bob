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

import io.prometheus.client.Gauge;

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
}
