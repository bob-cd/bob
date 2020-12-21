import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import crux.api.ICruxAPI;
import io.vertx.ext.web.RoutingContext;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
                .map(it -> it.get(0))
                .map(it -> makeProject((PersistentArrayMap) it))
                .collect(Collectors.toList());

            routingContext.response()
                .putHeader("Content-Type", "application/xml")
                .setStatusCode(200)
                .end(mapper.writeValueAsString(new Projects(projects)));
        } catch (Exception e) {
            Handlers.toJsonResponse(routingContext, e.getMessage(), 500);
        }
    }
}
