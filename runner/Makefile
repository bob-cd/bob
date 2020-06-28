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
		-e POSTGRES_DB=bob-test \
		-e POSTGRES_USER=bob \
		-e POSTGRES_PASSWORD=bob \
		--network bob-net \
		postgres:alpine
	docker run \
		-d \
		--name bob-test-db \
		-p 7779:7778 \
		-e BOB_STORAGE_HOST=bob-test-storage \
		-e BOB_STORAGE_DATABASE=bob-test \
		--network bob-net \
		bobcd/db
	docker run \
		-d \
		--name resource-git \
		-p 8000:8000 \
		--network bob-net \
		bobcd/resource-git
	sleep 7 # TODO: Find a better way to wait till things come up.
	clojure -A:test --fail-fast
	docker kill bob-test-storage bob-test-db resource-git && docker rm bob-test-storage bob-test-db resource-git
	docker network rm bob-net

compile:
	clojure -Spom && clojure -A:uberjar

clean:
	rm -rf .cpcache runner.jar pom.xml
