# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import copy
import os
import tempfile
import unittest
from unittest import mock

import microsoft_store_submission
import store_listings

LANGUAGES = ["de", "en"]
LISTINGS = {
    "en": {"shortDescription": "Songbook", "description": "Long", "features": ["One"], "keywords": ["chords"],
           "copyrightAndTrademarkInfo": "© 2026"},
    "de": {"shortDescription": "Liederbuch", "description": "Lang", "features": ["Eins"], "keywords": ["akkorde"],
           "copyrightAndTrademarkInfo": "© 2026"},
}


def submission(*locales):
    return {
        "id": "s1",
        "status": "PendingCommit",
        "fileUploadUrl": "https://upload.example/?sig=1",
        "applicationPackages": [{"fileName": "old.msix", "version": "1.0.0.0", "fileStatus": "Uploaded"}],
        "listings": {locale: {"baseListing": {
            "title": "Campfire", "shortDescription": "old", "description": "old", "features": [], "keywords": [],
            "copyrightAndTrademarkInfo": "old", "releaseNotes": "old", "images": [{"fileName": "shot.png"}],
        }} for locale in locales},
    }


class RunTest(unittest.TestCase):

    def run_main(self, draft, *, prepare=False, listings=True):
        calls = []

        def request(method, url, body=None):
            calls.append((method, url, copy.deepcopy(body)))
            if url.startswith("/applications?"):
                return {"value": [{"id": "app", "packageIdentityName": "Campfire", "pendingApplicationSubmission": {"id": "s1"}}]}
            if url == "/applications/app/submissions/s1":
                return copy.deepcopy(draft) if method == "GET" else None
            if url.endswith("/status"):
                return {"status": "Certification"}
            return None

        with tempfile.TemporaryDirectory() as folder:
            notes, package = os.path.join(folder, "notes.txt"), os.path.join(folder, "campfire.msix")
            with open(notes, "w", encoding="utf-8") as file:
                file.write("- New\n")
            with open(package, "wb") as file:
                file.write(b"msix")
            with mock.patch.object(microsoft_store_submission, "request", request), \
                    mock.patch.object(microsoft_store_submission, "upload") as upload, \
                    mock.patch.object(store_listings, "app_languages", return_value=LANGUAGES), \
                    mock.patch.object(store_listings, "read", return_value=copy.deepcopy(LISTINGS)), \
                    mock.patch.dict(os.environ, {"RELEASE_NOTES_BY_LANGUAGE": '{"de": "- Neu"}'}), \
                    mock.patch("builtins.print"):
                try:
                    microsoft_store_submission.main("Campfire", "2.0.0.0", package, notes, prepare, listings)
                except SystemExit:
                    pass
        puts = [body for method, url, body in calls if method == "PUT"]
        return puts, upload.called, calls

    def test_every_locale_gets_its_languages_fields_and_notes_and_keeps_its_images_and_title(self):
        puts, uploaded, _ = self.run_main(submission("en-us", "de-de", "de-at"))
        self.assertTrue(uploaded)
        listings = puts[0]["listings"]
        for locale, code in [("en-us", "en"), ("de-de", "de"), ("de-at", "de")]:
            base = listings[locale]["baseListing"]
            for field, text in LISTINGS[code].items():
                self.assertEqual(text, base[field])
            self.assertEqual("Campfire", base["title"])
            self.assertEqual([{"fileName": "shot.png"}], base["images"])
        self.assertEqual("- Neu", listings["de-at"]["baseListing"]["releaseNotes"])
        self.assertEqual("- New", listings["en-us"]["baseListing"]["releaseNotes"])

    def test_a_locale_of_a_language_the_app_lacks_is_left_alone(self):
        puts, _, _ = self.run_main(submission("en-us", "de-de", "ja-jp"))
        self.assertEqual(submission("ja-jp")["listings"]["ja-jp"], puts[0]["listings"]["ja-jp"])

    def test_an_app_language_without_a_listing_fails_before_anything_is_sent(self):
        puts, uploaded, calls = self.run_main(submission("en-us"))
        self.assertEqual([], puts)
        self.assertFalse(uploaded)
        self.assertFalse(any(method == "POST" and url.endswith("/commit") for method, url, _ in calls))

    def test_listings_off_writes_only_the_notes_and_takes_a_missing_listing(self):
        puts, uploaded, _ = self.run_main(submission("en-us"), listings=False)
        self.assertTrue(uploaded)
        base = puts[0]["listings"]["en-us"]["baseListing"]
        self.assertEqual("- New", base["releaseNotes"])
        self.assertEqual("old", base["description"])

    def test_prepare_writes_the_listings_and_uploads_nothing(self):
        puts, uploaded, _ = self.run_main(submission("en-us", "de-de"), prepare=True)
        self.assertFalse(uploaded)
        self.assertEqual("Lang", puts[0]["listings"]["de-de"]["baseListing"]["description"])
        self.assertEqual(["old.msix"], [package["fileName"] for package in puts[0]["applicationPackages"]])


if __name__ == "__main__":
    unittest.main()
