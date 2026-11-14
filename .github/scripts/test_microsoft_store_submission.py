# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import copy
import io
import os
import tempfile
import unittest
import zipfile
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


def with_images(draft):
    """The draft with a screenshot of each listing already in the Store and a poster art beside it."""
    for listing in draft["listings"].values():
        listing["baseListing"]["images"] = [
            {"id": "1", "fileName": "old.png", "fileStatus": "Uploaded", "imageType": "Screenshot"},
            {"id": "2", "fileName": "poster.png", "fileStatus": "Uploaded", "imageType": "PromotionalArtwork"},
        ]
    return draft


def write_screenshots(folder, *languages):
    """Two framed Windows screenshots per language and a box art that stays by hand, as the framing writes them."""
    for code in languages:
        directory = os.path.join(folder, "microsoft-store", code)
        os.makedirs(directory)
        for name in ["windows-2.png", "windows-1.png", "box-art.png"]:
            with open(os.path.join(directory, name), "wb") as file:
                file.write(f"{code}/{name}".encode())


class RunTest(unittest.TestCase):

    def run_main(self, draft, *, prepare=False, listings=True, screenshots=None):
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
            folder_of_screenshots = None
            if screenshots is not None:
                folder_of_screenshots = os.path.join(folder, "store")
                write_screenshots(folder_of_screenshots, *screenshots)
            with mock.patch.object(microsoft_store_submission, "request", request), \
                    mock.patch.object(microsoft_store_submission, "upload") as upload, \
                    mock.patch.object(store_listings, "app_languages", return_value=LANGUAGES), \
                    mock.patch.object(store_listings, "read", return_value=copy.deepcopy(LISTINGS)), \
                    mock.patch.dict(os.environ, {"RELEASE_NOTES_BY_LANGUAGE": '{"de": "- Neu"}'}), \
                    mock.patch("builtins.print"):
                try:
                    microsoft_store_submission.main("Campfire", "2.0.0.0", package, notes, prepare, listings,
                                                    folder_of_screenshots)
                except SystemExit:
                    pass
        puts = [body for method, url, body in calls if method == "PUT"]
        self.uploaded_images = list(upload.call_args.args[2]) if upload.called else None
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


    def test_screenshots_replace_the_listings_and_leave_the_other_images(self):
        puts, _, _ = self.run_main(with_images(submission("en-us", "de-de", "de-at")), screenshots=["en", "de"])
        for locale, code in [("en-us", "en"), ("de-de", "de"), ("de-at", "de")]:
            self.assertEqual([
                {"id": "1", "fileName": "old.png", "fileStatus": "PendingDelete", "imageType": "Screenshot"},
                {"id": "2", "fileName": "poster.png", "fileStatus": "Uploaded", "imageType": "PromotionalArtwork"},
                {"fileName": f"screenshot-{code}-windows-1.png", "fileStatus": "PendingUpload", "imageType": "Screenshot",
                 "description": ""},
                {"fileName": f"screenshot-{code}-windows-2.png", "fileStatus": "PendingUpload", "imageType": "Screenshot",
                 "description": ""},
            ], puts[0]["listings"][locale]["baseListing"]["images"])
        self.assertEqual(["screenshot-en-windows-1.png", "screenshot-en-windows-2.png",
                          "screenshot-de-windows-1.png", "screenshot-de-windows-2.png"], self.uploaded_images)

    def test_a_reused_draft_gets_the_screenshots_once(self):
        first, _, _ = self.run_main(with_images(submission("en-us", "de-de")), screenshots=["en", "de"])
        draft = first[0]
        draft.update({"status": "CommitFailed", "fileUploadUrl": "https://upload.example/?sig=2"})
        again, _, _ = self.run_main(draft, screenshots=["en", "de"])
        self.assertEqual(first[0]["listings"], again[0]["listings"])

    def test_prepare_leaves_the_images_alone(self):
        puts, uploaded, _ = self.run_main(with_images(submission("en-us", "de-de")), prepare=True, screenshots=["en", "de"])
        self.assertFalse(uploaded)
        self.assertEqual(with_images(submission("de-de"))["listings"]["de-de"]["baseListing"]["images"],
                         puts[0]["listings"]["de-de"]["baseListing"]["images"])

    def test_a_missing_language_without_screenshots_fails_before_anything_is_sent(self):
        puts, uploaded, _ = self.run_main(submission("en-us"), screenshots=["en"])
        self.assertEqual([], puts)
        self.assertFalse(uploaded)

    def test_a_missing_language_with_screenshots_gets_a_listing_copied_from_english(self):
        puts, uploaded, _ = self.run_main(with_images(submission("en-us")), screenshots=["en", "de"])
        self.assertTrue(uploaded)
        listing = puts[0]["listings"]["de"]
        self.assertEqual({}, listing["platformOverrides"])
        base = listing["baseListing"]
        for field, text in LISTINGS["de"].items():
            self.assertEqual(text, base[field])
        self.assertEqual("Campfire", base["title"])
        self.assertEqual("- Neu", base["releaseNotes"])
        self.assertEqual(["screenshot-de-windows-1.png", "screenshot-de-windows-2.png"],
                         [image["fileName"] for image in base["images"]])
        self.assertEqual({"PendingUpload"}, {image["fileStatus"] for image in base["images"]})
        self.assertIn("screenshot-de-windows-1.png", self.uploaded_images)

    def test_prepare_creates_a_missing_languages_text_without_images(self):
        puts, uploaded, _ = self.run_main(submission("en-us"), prepare=True)
        self.assertFalse(uploaded)
        base = puts[0]["listings"]["de"]["baseListing"]
        self.assertEqual("Lang", base["description"])
        self.assertEqual([], base["images"])


class UploadTest(unittest.TestCase):

    def test_the_images_go_into_the_zip_beside_the_package(self):
        sent = []
        with tempfile.TemporaryDirectory() as folder:
            package, image = os.path.join(folder, "campfire.msix"), os.path.join(folder, "windows-1.png")
            for path, content in [(package, b"msix"), (image, b"png")]:
                with open(path, "wb") as file:
                    file.write(content)
            with mock.patch.object(microsoft_store_submission, "blob_request", lambda url, data: sent.append(data)), \
                    mock.patch("builtins.print"):
                microsoft_store_submission.upload("https://upload.example/?sig=1", package,
                                                  {"screenshot-en-windows-1.png": image})
        with zipfile.ZipFile(io.BytesIO(b"".join(sent[:-1]))) as archive:
            self.assertEqual(b"msix", archive.read("campfire.msix"))
            self.assertEqual(b"png", archive.read("screenshot-en-windows-1.png"))


class FlagsTest(unittest.TestCase):

    def test_flags(self):
        self.assertEqual((True, False, "store"), microsoft_store_submission.parse_flags(
            ["--no-listings", "--screenshots", "store", "--prepare"]))
        self.assertEqual((False, True, None), microsoft_store_submission.parse_flags([]))
        self.assertIsNone(microsoft_store_submission.parse_flags(["--screenshots"]))
        self.assertIsNone(microsoft_store_submission.parse_flags(["--screenshots", "--prepare"]))
        self.assertIsNone(microsoft_store_submission.parse_flags(["--prepare", "--prepare"]))


if __name__ == "__main__":
    unittest.main()
