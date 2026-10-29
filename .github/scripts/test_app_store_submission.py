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
import store_listings

LISTINGS = {
    code: {"name": f"Campfire {code}", "subtitle": f"Sub {code}", "promotionalText": f"Promo {code}",
           "description": f"Description {code}", "keywords": f"keywords,{code}", "supportUrl": "https://s/",
           "marketingUrl": "https://m/", "copyright": "2026 Péter Pandula", "notes": "Review notes"}
    for code in ["de", "en"]
}


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



class WriteListingTest(unittest.TestCase):

    def run_listing(self, platform="IOS", locales=("en-US", "de-DE", "de-AT"), info_state="PREPARE_FOR_SUBMISSION",
                    has_earlier=True, write_listings=True, refuse=None):
        calls = []

        def request(method, path, body=None, missing_ok=False, conflict_ok=False):
            calls.append((method, path, body))
            if refuse and method == "PATCH" and path.startswith(refuse):
                raise SystemExit(1)
            if path.endswith("/appStoreVersionLocalizations"):
                return {"data": [{"id": f"v-{locale}", "attributes": {"locale": locale}} for locale in locales]}
            if path.endswith("/appInfos"):
                return {"data": [{"id": "live", "attributes": {"state": "READY_FOR_DISTRIBUTION"}},
                                 {"id": "next", "attributes": {"state": info_state}}]}
            if path.endswith("/appInfoLocalizations"):
                return {"data": [{"id": f"i-{locale}", "attributes": {"locale": locale}} for locale in locales]}
            if path.endswith("/appStoreReviewDetail"):
                return None
            if path.startswith("/appStoreVersions/version?"):
                return {"data": {"attributes": {"copyright": "2025"}}}
            return None

        notes = {"de": "- Neu", "en": "- New"}
        with mock.patch.object(app_store_submission, "request", request), \
                mock.patch.object(store_listings, "app_languages", return_value=["de", "en"]), \
                mock.patch.object(store_listings, "read", return_value=LISTINGS), \
                mock.patch("builtins.print"):
            try:
                app_store_submission.write_listing("app", platform, {"id": "version"}, notes.get, has_earlier, write_listings)
                failed = False
            except SystemExit:
                failed = True
        patches = {path: body["data"]["attributes"] for method, path, body in calls if method == "PATCH" and body}
        return patches, calls, failed

    def test_every_localization_gets_its_languages_text_and_notes(self):
        patches, calls, failed = self.run_listing()
        self.assertFalse(failed)
        self.assertEqual({
            "description": "Description de", "keywords": "keywords,de", "promotionalText": "Promo de",
            "supportUrl": "https://s/", "marketingUrl": "https://m/", "whatsNew": "- Neu",
        }, patches["/appStoreVersionLocalizations/v-de-AT"])
        self.assertEqual("- New", patches["/appStoreVersionLocalizations/v-en-US"]["whatsNew"])
        self.assertEqual({"name": "Campfire de", "subtitle": "Sub de"}, patches["/appInfoLocalizations/i-de-DE"])
        self.assertEqual({"copyright": "2026 Péter Pandula"}, patches["/appStoreVersions/version"])
        self.assertTrue(any(method == "POST" and path == "/appStoreReviewDetails" and body["data"]["attributes"] == {"notes": "Review notes"}
                            for method, path, body in calls))

    def test_the_mac_never_touches_the_name_and_subtitle(self):
        _, calls, failed = self.run_listing(platform="MAC_OS")
        self.assertFalse(failed)
        self.assertFalse(any("appInfo" in path for _, path, _ in calls))

    def test_an_app_language_without_a_localization_fails_before_anything_is_written(self):
        patches, _, failed = self.run_listing(locales=("en-US",))
        self.assertTrue(failed)
        self.assertEqual({}, patches)

    def test_listings_off_writes_only_the_notes_and_takes_a_missing_localization(self):
        patches, calls, failed = self.run_listing(locales=("en-US",), write_listings=False)
        self.assertFalse(failed)
        self.assertEqual({"/appStoreVersionLocalizations/v-en-US": {"whatsNew": "- New"}}, patches)

    def test_a_first_version_gets_no_whats_new(self):
        patches, _, _ = self.run_listing(has_earlier=False)
        self.assertNotIn("whatsNew", patches["/appStoreVersionLocalizations/v-en-US"])

    def test_no_editable_app_info_leaves_the_name_for_the_next_version(self):
        patches, _, failed = self.run_listing(info_state="WAITING_FOR_REVIEW")
        self.assertFalse(failed)
        self.assertFalse(any(path.startswith("/appInfoLocalizations") for path in patches))
        self.assertIn("/appStoreVersionLocalizations/v-de-DE", patches)

    def test_a_name_apple_refuses_fails_the_run(self):
        # write_listing runs before submit in main, so the version is never submitted with the old name.
        _, _, failed = self.run_listing(refuse="/appInfoLocalizations")
        self.assertTrue(failed)


if __name__ == "__main__":
    unittest.main()
