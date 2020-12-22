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

import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import crux.api.ICruxAPI;
import io.vertx.ext.web.RoutingContext;

import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JacksonXmlRootElement(localName = "Projects") record Projects(
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Project> Project
) {
}

record Project(
    String name,
    String activity,
    String lastBuildStatus,
    String lastBuildLabel,
    String lastBuildTime,
    String webUrl
) {
}

public class CCTray {
    final static XmlMapper mapper = new XmlMapper();

    private static List<Project> gatherLatest(Stream<Project> projects) {
        final var seen = new HashSet<String>();

        return projects
            .filter(project -> seen.add(project.name()))
            .collect(Collectors.toList());
    }

    private static Project makeProject(PersistentArrayMap data) {
        final var group = data.get(Keyword.intern(Symbol.create("group")));
        final var name = data.get(Keyword.intern(Symbol.create("name")));
        final var lastBuildTime = ((Date) data.get(Keyword.intern(Symbol.create("completed")))).toInstant().toString();
        final var status = ((Keyword) data.get(Keyword.intern(Symbol.create("status")))).getName();
        final var lastBuildLabel = data.get(Keyword.intern("crux.db", "id")).toString().split("/")[1];
        final var lastBuildStatus = switch (status) {
            case "passed", "running", "paused" -> "Success";
            case "failed" -> "Failure";
            case "stopped" -> "Exception";
            default -> "Unknown";
        };

        return new Project(
            "%s:%s".formatted(group, name),
            status.equals("running") ? "Running" : "Sleeping",
            lastBuildStatus,
            lastBuildLabel,
            lastBuildTime,
            "#"
        );
    }

    public static void generateInfo(RoutingContext routingContext, ICruxAPI node) {
        final var query = DB.datafy(
            """
            {:find  [(eql/project run [:group :name :status :completed :crux.db/id])]
             :where [[pipeline :type :pipeline]
                     [pipeline :group group]
                     [pipeline :name name]
                     [run :type :pipeline-run]
                     [run :group group]
                     [run :name name]]}
            """
        );

        try {
            final var projects = node
                .db()
                .query(query)
                .stream()
                .map(it -> (PersistentArrayMap) it.get(0))
                .map(CCTray::makeProject)
                .sorted(Comparator.comparing(Project::lastBuildTime).reversed());

            routingContext.response()
                .putHeader("Content-Type", "application/xml")
                .setStatusCode(200)
                .end(mapper.writeValueAsString(new Projects(gatherLatest(projects))));
        } catch (Exception e) {
            Handlers.toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }
}
