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
	docker run \
		-d \
		--name bob-test-db \
		-p 5433:5432 \
		-e POSTGRES_DB=bob-test \
		-e POSTGRES_USER=bob \
		-e POSTGRES_PASSWORD=bob \
		postgres:alpine
	clojure -A:test
	docker kill bob-test-db && docker rm bob-test-db

compile:
	clojure -Spom && clojure -A:uberjar

clean:
	rm -rf .cpcache entities.jar pom.xml
