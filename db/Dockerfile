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

FROM clojure:openjdk-14-tools-deps as builder

WORKDIR /opt
COPY . .
RUN make compile


FROM openjdk:14.0.1-slim

WORKDIR /opt
COPY --from=builder /opt/db.jar .

ENTRYPOINT ["java", "-jar", "db.jar"]
