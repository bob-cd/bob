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

import os
import sys
import time
import json
import signal
import subprocess
from urllib import request
from urllib.error import URLError
from urllib.parse import urljoin

CURRENT_DIR = os.path.abspath(os.path.dirname(__file__))
CONFIG_FILE = os.path.join(CURRENT_DIR, "config.json")

with open(CONFIG_FILE) as json_data:
    CONFIG = json.load(json_data)

BASE_URL = "{}://{}:{}".format(
    CONFIG["protocol"], CONFIG["host"], CONFIG["port"]
)


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

        if "wait" in test:
            print(
                "Waiting for {} seconds before {}.".format(
                    test["wait"], test["name"]
                )
            )
            time.sleep(test["wait"])

        if test["method"] == "GET":
            req = request.Request(url)
        elif test["method"] == "POST":
            data = json.dumps(test["data"]).encode("utf8")
            req = request.Request(
                url, data=data, headers={"Content-Type": "application/json"}
            )
        elif test["method"] == "DELETE":
            req = request.Request(
                url, headers={"Content-Type": "application/json"}
            )
            req.get_method = lambda: "DELETE"
        else:
            sys.stderr.write(
                "Unknown request method {} at test {}.\n".format(
                    test["method"], test["name"]
                )
            )

            sys.exit(1)

        response = {}

        try:
            with request.urlopen(req) as res:
                response = json.loads(res.read().decode("utf-8"))

            assert response == test["response"]
        except AssertionError:
            sys.stderr.write(
                "{} failed with response {}".format(
                    test["name"], json.dumps(response, indent=2)
                )
            )

            sys.exit(1)

        print("{} passed.".format(test["name"]))


def clean_up(gc_url, bob):
    print("Cleaning up.")

    request.Request(url)

    bob.send_signal(signal.SIGTERM)

    print("Tests failed, bob stopped.")


if __name__ == "__main__":
    bob = subprocess.Popen(["boot", "run"])
    print("Bob started.")

    wait_for_it()

    try:
        run_tests()
    except Exception as _:
        clean_up(bob, urljoin(BASE_URL, "gc/all"))
        sys.exit(-1)

    print("All checks passed!")

    print("Stopping bob.")
    bob.send_signal(signal.SIGTERM)
    print("Bob stopped.")
