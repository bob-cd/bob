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

entities-dir := "entities"
runner-dir := "runner"
apiserver-dir := "apiserver"

.PHONY: test $(entities-dir) $(runner-dir) $(apiserver-dir)

all: entities runner apiserver

test: test-entities test-runner test-apiserver

compile: entities runner apiserver

docker-image: docker-image-entities docker-image-runner docker-image-apiserver

docker-push: docker-push-entities docker-push-runner docker-push-apiserver

entities: $(entities-dir)
	$(MAKE) --directory=$(entities-dir) compile

runner: $(runner-dir)
	$(MAKE) --directory=$(runner-dir) compile

apiserver: $(apiserver-dir)
	$(MAKE) --directory=$(apiserver-dir) compile

test-entities: $(entities-dir)
	$(MAKE) --directory=$(entities-dir) test

test-runner: $(runner-dir)
	$(MAKE) --directory=$(runner-dir) test

test-apiserver: $(apiserver-dir)
	$(MAKE) --directory=$(apiserver-dir) test

docker-image-entities: $(entities-dir)
	$(MAKE) --directory=$(entities-dir) docker-image

docker-image-runner: $(runner-dir)
	$(MAKE) --directory=$(runner-dir) docker-image

docker-image-apiserver: $(apiserver-dir)
	$(MAKE) --directory=$(apiserver-dir) docker-image

docker-push-entities: $(entities-dir)
	$(MAKE) --directory=$(entities-dir) docker-push

docker-push-runner: $(runner-dir)
	$(MAKE) --directory=$(runner-dir) docker-push

docker-push-apiserver: $(apiserver-dir)
	$(MAKE) --directory=$(apiserver-dir) docker-push

clean:
	$(MAKE) --directory=$(runner-dir) clean
	$(MAKE) --directory=$(entities-dir) clean
	$(MAKE) --directory=$(apiserver-dir) clean
