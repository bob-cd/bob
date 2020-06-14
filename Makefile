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

entities_dir := "entities"
runner_dir := "runner"

.PHONY: test $(entities_dir) $(runner_dir)

all: entities runner

test: test_entities test_runner

entities: $(entities_dir)
	$(MAKE) --directory=$(entities_dir) compile

runner: $(consumer_dir)
	$(MAKE) --directory=$(runner_dir) compile

test_entities: $(producer_dir)
	$(MAKE) --directory=$(entities_dir) test

test_runner: $(consumer_dir)
	$(MAKE) --directory=$(runner_dir) test

clean:
	$(MAKE) --directory=$(runner_dir) clean
	$(MAKE) --directory=$(entities_dir) clean
