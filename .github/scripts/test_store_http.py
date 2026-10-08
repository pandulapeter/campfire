# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import contextlib
import http.client
import io
import unittest
import urllib.error
from unittest import mock

from store_http import request_json, should_retry


def http_error(code, body=b""):
    return urllib.error.HTTPError("https://example.com", code, "answer", {}, io.BytesIO(body))


class Response:

    def __init__(self, content):
        self.content = content

    def read(self):
        return self.content

    def __enter__(self):
        return self

    def __exit__(self, *arguments):
        return False


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


@mock.patch("store_http.time.sleep")
class RequestJsonTest(unittest.TestCase):

    def call(self, urlopen, *arguments, **keywords):
        token = mock.Mock(return_value="token")
        output = io.StringIO()
        with mock.patch("store_http.urllib.request.urlopen", urlopen), contextlib.redirect_stdout(output), \
                contextlib.redirect_stderr(output):
            result = request_json(*arguments, token=token, **keywords)
        return result, token, output.getvalue()

    def test_a_get_the_service_failed_twice_is_asked_again_with_a_fresh_token(self, _):
        urlopen = mock.Mock(side_effect=[http_error(503), http_error(503), Response(b'{"data": 1}')])
        result, token, output = self.call(urlopen, "GET", "https://example.com/builds")
        self.assertEqual(result, {"data": 1})
        self.assertEqual(token.call_count, 3)
        self.assertIn("GET https://example.com/builds failed (503), asking again in 10 s.", output)

    def test_a_post_the_service_failed_is_not_asked_again(self, _):
        urlopen = mock.Mock(side_effect=[http_error(503, b"busy")])
        with self.assertRaises(SystemExit):
            self.call(urlopen, "POST", "https://example.com/builds", body={"a": 1})
        self.assertEqual(urlopen.call_count, 1)

    def test_an_answered_status_is_returned_instead_of_failing(self, _):
        urlopen = mock.Mock(side_effect=[http_error(404)])
        result, _, output = self.call(urlopen, "GET", "https://example.com/builds/1", answers={404: None})
        self.assertIsNone(result)
        self.assertEqual(output, "")

    def test_an_answered_conflict_is_still_named(self, _):
        urlopen = mock.Mock(side_effect=[http_error(409, b"taken")])
        result, _, output = self.call(urlopen, "POST", "https://example.com/v1/x", answers={409: None}, label="/x")
        self.assertIsNone(result)
        self.assertEqual(output, "POST /x answered 409: taken\n")

    def test_an_empty_body_is_none(self, _):
        result, _, _ = self.call(mock.Mock(return_value=Response(b"")), "DELETE", "https://example.com/builds/1")
        self.assertIsNone(result)

    def test_a_dropped_connection_on_a_get_is_asked_again(self, _):
        urlopen = mock.Mock(side_effect=[ConnectionResetError("reset"), Response(b"[]")])
        result, token, _ = self.call(urlopen, "GET", "https://example.com/builds")
        self.assertEqual(result, [])
        self.assertEqual(token.call_count, 2)


if __name__ == "__main__":
    unittest.main()
