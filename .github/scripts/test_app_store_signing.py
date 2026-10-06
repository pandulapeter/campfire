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
from datetime import datetime, timedelta, timezone

from app_store_signing import RENEWAL_DAYS, needs_renewal, newest_valid, parse_expiration, should_retry

NOW = datetime(2026, 10, 2, 12, tzinfo=timezone.utc)


def certificate(identifier, days_left):
    return {"id": identifier, "expiration": NOW + timedelta(days=days_left)}


def http_error(code):
    return urllib.error.HTTPError("https://example.com", code, "answer", {}, None)


class ParseExpirationTest(unittest.TestCase):

    def test_the_format_the_api_answers_with(self):
        self.assertEqual(
            parse_expiration("2027-09-29T19:26:01.000+00:00"),
            datetime(2027, 9, 29, 19, 26, 1, tzinfo=timezone.utc),
        )

    def test_a_trailing_z(self):
        self.assertEqual(parse_expiration("2027-09-29T19:26:01Z"), datetime(2027, 9, 29, 19, 26, 1, tzinfo=timezone.utc))


class NewestValidTest(unittest.TestCase):

    def test_none_where_there_is_none(self):
        self.assertIsNone(newest_valid([], NOW))

    def test_none_where_every_one_has_expired(self):
        self.assertIsNone(newest_valid([certificate("a", -1), certificate("b", -300)], NOW))

    def test_the_one_that_expires_last(self):
        found = newest_valid([certificate("old", 20), certificate("renewed", 330), certificate("expired", -5)], NOW)
        self.assertEqual(found["id"], "renewed")


class NeedsRenewalTest(unittest.TestCase):

    def test_not_while_more_than_the_renewal_period_is_left(self):
        self.assertFalse(needs_renewal(certificate("a", RENEWAL_DAYS + 1), NOW))

    def test_once_less_is_left(self):
        self.assertTrue(needs_renewal(certificate("a", RENEWAL_DAYS - 1), NOW))


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
