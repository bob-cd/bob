#!/usr/bin/python3

# This file is part of Bob.
#
# Bob is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Bob is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Bob. If not, see <http://www.gnu.org/licenses/>.

import json
import subprocess
from urllib import request
from urllib.error import URLError
from urllib.parse import urljoin

import time

with open("integration-tests/config.json") as json_data:
    CONFIG = json.load(json_data)

BASE_URL = "{}://{}:{}".format(
    CONFIG["protocol"], CONFIG["host"], CONFIG["port"]
)


def start_bob():
    # TODO: Store PID and kill it post test.

    subprocess.Popen(["lein", "run"])
    print("Started bob.")


def wait_for_it():
    # TODO: HORRIBLE way to wait. Please improve.

    while True:
        try:
            req = request.Request(BASE_URL)

            with request.urlopen(req) as _:
                pass
        except (ConnectionRefusedError, URLError):
            print("Waiting for bob at {}".format(BASE_URL))
            time.sleep(1)

            continue

        break


def run_tests():
    for test in CONFIG["tests"]:
        print("Testing {}.".format(test["name"]))

        url = urljoin(BASE_URL, test["path"])

        if test["method"] == "GET":
            req = request.Request(url)

            with request.urlopen(req) as res:
                expected = json.loads(res.read().decode("utf-8"))

                assert expected == test["response"]
        else:
            raise Exception(
                "Unknown method {} at test {}.".format(
                    test["method"], test["name"]
                )
            )

        print("{} passed.".format(test["name"]))


if __name__ == "__main__":
    start_bob()

    wait_for_it()

    run_tests()

    print("All checks passed!")
