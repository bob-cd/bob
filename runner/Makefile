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

all: test compile

.PHONY: test
test:
	docker network create bob-net
	docker run \
		-d \
		--name bob-test-storage \
		-p 5433:5432 \
		-e POSTGRES_DB=bob-test \
		-e POSTGRES_USER=bob \
		-e POSTGRES_PASSWORD=bob \
		--network bob-net \
		postgres:alpine
	docker run \
		-d \
		--name bob-test-queue \
		-p 5673:5672 \
		--network bob-net \
		rabbitmq:3-alpine
	docker run \
		-d \
		--name resource-git \
		-p 8000:8000 \
		--network bob-net \
		bobcd/resource-git
	docker run \
		-d \
		--name artifact-local \
		-p 8001:8001 \
		--network bob-net \
		bobcd/artifact-local
	sleep 7 # TODO: Find a better way to wait till things come up.
	clojure -A:test --fail-fast
	docker kill bob-test-storage bob-test-queue resource-git artifact-local && docker rm bob-test-storage bob-test-queue resource-git artifact-local
	docker network rm bob-net

compile:
	clojure -Spom && clojure -A:uberjar

docker-image: compile
	docker build -t bobcd/runner .

docker-push: docker-image
	docker push bobcd/runner

clean:
	rm -rf .cpcache runner.jar pom.xml
	docker rmi bobcd/runner || true
