# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import http.client
import unittest
import urllib.error

from microsoft_store_submission import should_retry


def http_error(code):
    return urllib.error.HTTPError("https://example.com", code, "answer", {}, None)


class ShouldRetryTest(unittest.TestCase):

    def test_should_retry_a_get_the_service_or_the_network_failed(self):
        for error in [
            http_error(503), http_error(502), http_error(429),
            urllib.error.URLError("reset"), TimeoutError(), ConnectionResetError(),
            http.client.RemoteDisconnected("Remote end closed connection without response"),
        ]:
            self.assertTrue(should_retry("GET", error, 1, 4))
            self.assertFalse(should_retry("GET", error, 4, 4))

    def test_should_not_retry_an_answer_or_a_change(self):
        for code in [404, 401, 409]:
            self.assertFalse(should_retry("GET", http_error(code), 1, 4))
        for method in ["POST", "PATCH"]:
            self.assertFalse(should_retry(method, http_error(503), 1, 4))


if __name__ == "__main__":
    unittest.main()
