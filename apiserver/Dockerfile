# Copyright 2018-2021 Rahul De
#
# Use of this source code is governed by an MIT-style
# license that can be found in the LICENSE file or at
# https://opensource.org/licenses/MIT.

FROM eclipse-temurin:17-jdk AS jdk

RUN jlink \
    --compress=2 \
    --module-path jmods \
    --add-modules java.base,java.sql,jdk.unsupported,java.naming,java.desktop,java.management,java.net.http \
    --output jlinked

FROM ubuntu:latest

COPY --from=jdk /jlinked jdk
COPY apiserver.jar .

ENTRYPOINT ["/jdk/bin/java", "-jar", "apiserver.jar"]
