#   This file is part of Bob.
#
#   Bob is free software: you can redistribute it and/or modify
#   it under the terms of the GNU Affero General Public License as published by
#   the Free Software Foundation, either version 3 of the License, or
#   (at your option) any later version.
#
#   Bob is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
#   GNU Affero General Public License for more details.
#
#   You should have received a copy of the GNU Affero General Public License
#   along with Bob. If not, see <http://www.gnu.org/licenses/>.

FROM eclipse-temurin:17-jdk AS jdk

RUN jlink \
    --compress=2 \
    --module-path jmods \
    --add-modules java.base,java.sql,jdk.unsupported,java.naming,java.desktop,java.management,java.net.http \
    --output jlinked

# Based on Fedora
FROM quay.io/podman/stable:latest

COPY --from=jdk /jlinked jdk
COPY runner.jar .
RUN echo -e "set -e \n\
podman system service -t 0 unix:///tmp/pod.sock & \n\
/jdk/bin/java -jar runner.jar" > entrypoint.sh
ENV CONTAINER_ENGINE_URL "unix:///tmp/pod.sock"

ENTRYPOINT ["sh", "entrypoint.sh"]
