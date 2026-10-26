# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest
from unittest import mock

# Importing it is half the test: a name it takes from another script that is no longer there fails the release's last step.
import app_store_submission


def version(state):
    return {"attributes": {"versionString": "1.0.0", "appStoreState": state}}


class AwaitingVersionsTest(unittest.TestCase):

    def test_keeps_only_the_versions_submitted_and_not_on_the_store_yet(self):
        listed = [version(state) for state in (
            "PREPARE_FOR_SUBMISSION", "READY_FOR_REVIEW", "REJECTED", "WAITING_FOR_REVIEW", "IN_REVIEW",
            "PENDING_DEVELOPER_RELEASE", "READY_FOR_SALE",
        )]
        with mock.patch.object(app_store_submission, "request", return_value={"data": listed}):
            states = [item["attributes"]["appStoreState"] for item in app_store_submission.awaiting_versions("1", "MAC_OS")]
        self.assertEqual(states, ["WAITING_FOR_REVIEW", "IN_REVIEW", "PENDING_DEVELOPER_RELEASE"])

    def test_no_awaiting_state_is_also_editable(self):
        self.assertFalse(app_store_submission.AWAITING_STATES & app_store_submission.EDITABLE_STATES)


if __name__ == "__main__":
    unittest.main()
