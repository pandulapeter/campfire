# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest
from datetime import datetime, timedelta, timezone

from app_store_signing import RENEWAL_DAYS, needs_renewal, newest_valid, parse_expiration

NOW = datetime(2026, 10, 2, 12, tzinfo=timezone.utc)


def certificate(identifier, days_left):
    return {"id": identifier, "expiration": NOW + timedelta(days=days_left)}


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


if __name__ == "__main__":
    unittest.main()
