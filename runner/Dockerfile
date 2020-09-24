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

FROM alpine:latest AS jdk

RUN wget https://cdn.azul.com/zulu/bin/zulu15.27.17-ca-jdk15.0.0-linux_musl_x64.tar.gz
RUN tar -zxvf *.tar.gz
RUN rm *.tar.gz
RUN mv zulu* jdk

WORKDIR /jdk

# TODO: See if these modules are enough?
RUN bin/jlink --compress=2 --module-path jmods --add-modules java.base,java.sql,jdk.unsupported,java.naming,java.desktop --output jlinked


FROM docker:dind

COPY --from=jdk /jdk/jlinked /opt/jdk/

COPY runner.jar /opt/runner.jar
COPY entrypoint.sh /opt
RUN chmod +x /opt/entrypoint.sh

ENTRYPOINT ["/opt/entrypoint.sh"]
