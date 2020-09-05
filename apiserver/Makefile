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

all: setup test compile

.PHONY: setup teardown test
setup:
	docker network create bob-net --subnet 10.0.76.0/24
	docker run \
		--detach \
		--name bob-storage \
		-e POSTGRES_DB=bob \
		-e POSTGRES_USER=bob \
		-e POSTGRES_PASSWORD=bob \
		--publish 5432:5432 \
		--network bob-net \
		postgres:alpine
	docker run \
		--detach \
		--name bob-queue \
		--publish 5672:5672 \
		--publish 15672:15672 \
		--network bob-net \
		rabbitmq:3-management-alpine

teardown:
	docker rm -f bob-storage bob-queue bob-db
	docker network rm bob-net

test:
	docker network create bob-test-net --subnet 10.0.77.0/24
	docker run \
		-d \
		--name bob-test-storage \
		-e POSTGRES_DB=bob-test \
		-e POSTGRES_USER=bob \
		-e POSTGRES_PASSWORD=bob \
		--network bob-test-net \
		postgres:alpine
	docker run \
		-d \
		--name bob-test-queue \
		-p 5673:5672 \
		--network bob-test-net \
		rabbitmq:3-alpine
	sleep 7 # TODO: Find a better way to wait till things come up.
	gradle test
	docker kill bob-test-storage bob-test-queue bob-test-db && docker rm bob-test-storage bob-test-queue bob-test-db
	docker network rm bob-net

compile:
	gradle shadowJar

clean:
	rm -rf build/ bin/ gradle/ .gradle/ .settings/ .classpath .project
