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

FROM clojure:boot as builder

WORKDIR /opt
COPY . .
RUN boot show --deps
RUN boot build


FROM docker:dind

WORKDIR /opt
RUN apk add -u wget
RUN wget "https://cdn.azul.com/zulu/bin/zulu12.3.11-ca-jdk12.0.2-linux_musl_x64.tar.gz"
RUN tar -zxvf *.tar.gz
RUN rm *.tar.gz
RUN mv zulu* jdk
COPY --from=builder /opt/target/bob-standalone.jar .
COPY bob-entrypoint.sh /opt
RUN chmod +x /opt/bob-entrypoint.sh


ENTRYPOINT ["/opt/bob-entrypoint.sh"]
