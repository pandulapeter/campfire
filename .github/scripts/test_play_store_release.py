# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import json
import os
import subprocess
import tempfile
import unittest
from unittest import mock

import play_store_release
import store_listings
from pathlib import Path

LISTINGS = {
    "en": {"title": "Campfire", "shortDescription": "Songbook", "fullDescription": "Long"},
    "de": {"title": "Lagerfeuer", "shortDescription": "Liederbuch", "fullDescription": "Lang"},
}


class CutTest(unittest.TestCase):

    def test_notes_within_the_limit_are_kept_whole(self):
        text = "- " + "a" * 248 + "\n- " + "b" * 247
        self.assertEqual((text, False), play_store_release.cut(text))

    def test_notes_over_the_limit_keep_the_lines_that_fit(self):
        self.assertEqual(("- one", True), play_store_release.cut("- one\n- " + "x" * 500))

    def test_a_first_line_over_the_limit_is_cut_at_a_word(self):
        text, was_cut = play_store_release.cut("word " * 200)
        self.assertTrue(was_cut)
        self.assertLessEqual(len(text), 500)
        self.assertTrue(text.endswith("word"))


class SignedJwtTest(unittest.TestCase):

    def test_the_assertion_is_signed_with_the_accounts_key(self):
        with tempfile.TemporaryDirectory() as folder:
            key = os.path.join(folder, "key.pem")
            subprocess.run(["openssl", "genrsa", "-out", key, "2048"], capture_output=True, check=True)
            with open(key) as file:
                account = {"client_email": "ci@example.iam", "token_uri": "https://oauth2.example/token", "private_key": file.read()}
            jwt = play_store_release.signed_jwt(account, 1000)
            header, claims, signature = jwt.split(".")
            decode = lambda part: json.loads(play_store_release.base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))
            self.assertEqual({"alg": "RS256", "typ": "JWT"}, decode(header))
            self.assertEqual({"iss": "ci@example.iam", "scope": play_store_release.SCOPE, "aud": "https://oauth2.example/token",
                              "iat": 1000, "exp": 4600}, decode(claims))
            public = os.path.join(folder, "public.pem")
            subprocess.run(["openssl", "rsa", "-in", key, "-pubout", "-out", public], capture_output=True, check=True)
            signature_file = os.path.join(folder, "signature")
            with open(signature_file, "wb") as file:
                file.write(play_store_release.base64.urlsafe_b64decode(signature + "=" * (-len(signature) % 4)))
            verified = subprocess.run(["openssl", "dgst", "-sha256", "-verify", public, "-signature", signature_file],
                                      input=f"{header}.{claims}".encode(), capture_output=True)
            self.assertEqual(0, verified.returncode, verified.stderr)


class RunTest(unittest.TestCase):

    def run_main(self, locales, *, draft=False, listings=True, images=None):
        """The calls main makes, with images ({language: [file names]}) framed into a store folder when given."""
        calls = []

        def request(method, path, body=None):
            calls.append((method, path, body))
            if path.endswith("/edits"):
                return {"id": "e1"}
            if path.endswith("/listings") and method == "GET":
                return {"listings": [{"language": locale, "title": "old", "shortDescription": "old", "fullDescription": "old"}
                                     for locale in locales]}
            return None

        def upload(path, file, content_type):
            calls.append(("UPLOAD", path, os.path.basename(file)))
            return {"versionCode": 7}

        with tempfile.TemporaryDirectory() as folder:
            paths = {name: os.path.join(folder, name) for name in ["notes.txt", "app.apk", "mapping.txt"]}
            for name, path in paths.items():
                with open(path, "w", encoding="utf-8") as file:
                    file.write("- New\n" if name == "notes.txt" else "x")
            screenshots = None
            if images is not None:
                screenshots = os.path.join(folder, "store")
                for code, names in images.items():
                    language = Path(screenshots, "google-play", code)
                    language.mkdir(parents=True)
                    for name in names:
                        (language / name).write_bytes(b"png")
            with mock.patch.object(play_store_release, "request", request), \
                    mock.patch.object(play_store_release, "upload", upload), \
                    mock.patch.object(store_listings, "app_languages", return_value=["de", "en"]), \
                    mock.patch.object(store_listings, "read", return_value=LISTINGS), \
                    mock.patch.dict(os.environ, {"RELEASE_NOTES_BY_LANGUAGE": '{"de": "- Neu"}'}), \
                    mock.patch("builtins.print"):
                try:
                    play_store_release.main("com.example", paths["app.apk"], paths["mapping.txt"], "5.1.0 (510)", "3",
                                            paths["notes.txt"], draft, listings, screenshots)
                except SystemExit:
                    pass
        return calls

    def test_one_edit_carries_the_release_its_notes_per_locale_and_the_listings(self):
        calls = self.run_main(["en-US", "de-DE", "de-AT"])
        track = next(body for method, path, body in calls if method == "PUT")["releases"][0]
        self.assertEqual({"name": "5.1.0 (510)", "versionCodes": ["7"], "status": "completed", "inAppUpdatePriority": 3,
                          "releaseNotes": [{"language": "en-US", "text": "- New"}, {"language": "de-DE", "text": "- Neu"},
                                           {"language": "de-AT", "text": "- Neu"}]}, track)
        patches = {path: body for method, path, body in calls if method == "PATCH"}
        self.assertEqual({"language": "de-AT", **LISTINGS["de"]}, patches["/com.example/edits/e1/listings/de-AT"])
        self.assertEqual(("POST", "/com.example/edits/e1:commit?changesNotSentForReview=false", {}), calls[-1])

    def test_an_app_language_without_a_listing_gets_one_before_the_notes_name_it(self):
        calls = self.run_main(["en-US"])
        create = calls.index(("PUT", "/com.example/edits/e1/listings/de-DE", {"language": "de-DE", **LISTINGS["de"]}))
        track = next(index for index, (method, path, _) in enumerate(calls) if path.endswith("/tracks/production"))
        apk = next(index for index, (method, path, _) in enumerate(calls) if path.endswith("/apks"))
        self.assertLess(create, apk)
        self.assertLess(apk, track)
        self.assertEqual([{"language": "en-US", "text": "- New"}, {"language": "de-DE", "text": "- Neu"}],
                         calls[track][2]["releases"][0]["releaseNotes"])
        self.assertTrue(calls[-1][1].endswith(":commit?changesNotSentForReview=false"))

    def test_listings_off_skip_an_app_language_without_a_listing(self):
        calls = self.run_main(["en-US"], listings=False, images={"en": ["phone-1.png", "phone-2.png"]})
        self.assertFalse(any(method in {"PATCH", "DELETE"} or "/listings/" in path for method, path, _ in calls))
        track = next(body for method, path, body in calls if method == "PUT")["releases"][0]
        self.assertEqual([{"language": "en-US", "text": "- New"}], track["releaseNotes"])
        self.assertTrue(calls[-1][1].endswith(":commit?changesNotSentForReview=false"))

    def test_screenshots_replace_each_set_of_every_locale_in_the_listings_order(self):
        calls = self.run_main(["en-US", "de-DE", "de-AT"], images={
            "en": ["phone-2.png", "phone-1.png", "feature-graphic.png", "chromebook-1.png"],
            "de": ["large-tablet-1.png"],
        })
        images = [(method, path.split("/listings/")[1], file) for method, path, file in calls
                  if method in {"DELETE", "UPLOAD"} and "/listings/" in path]
        self.assertEqual([
            ("DELETE", "en-US/phoneScreenshots", None),
            ("UPLOAD", "en-US/phoneScreenshots", "phone-1.png"),
            ("UPLOAD", "en-US/phoneScreenshots", "phone-2.png"),
            ("DELETE", "en-US/featureGraphic", None),
            ("UPLOAD", "en-US/featureGraphic", "feature-graphic.png"),
            ("DELETE", "de-DE/tenInchScreenshots", None),
            ("UPLOAD", "de-DE/tenInchScreenshots", "large-tablet-1.png"),
            ("DELETE", "de-AT/tenInchScreenshots", None),
            ("UPLOAD", "de-AT/tenInchScreenshots", "large-tablet-1.png"),
        ], images)
        last_image = max(index for index, (method, path, _) in enumerate(calls)
                         if "/listings/" in path and method != "GET")
        apk = next(index for index, (method, path, _) in enumerate(calls) if path.endswith("/apks"))
        self.assertLess(last_image, apk)

    def test_a_new_language_gets_its_images_after_its_listing(self):
        calls = self.run_main(["en-US"], images={"de": ["phone-1.png"]})
        paths = [(method, path) for method, path, _ in calls if "/listings/de-DE" in path]
        self.assertEqual([("PUT", "/com.example/edits/e1/listings/de-DE"),
                          ("DELETE", "/com.example/edits/e1/listings/de-DE/phoneScreenshots"),
                          ("UPLOAD", "/com.example/edits/e1/listings/de-DE/phoneScreenshots")], paths)

    def test_without_screenshots_no_image_is_touched(self):
        calls = self.run_main(["en-US", "de-DE"])
        self.assertFalse(any(method == "DELETE" or (method == "UPLOAD" and "/listings/" in path)
                             for method, path, _ in calls))

    def test_listings_off_and_a_draft_write_only_the_notes(self):
        calls = self.run_main(["en-US"], draft=True, listings=False)
        self.assertFalse(any(method == "PATCH" for method, _, _ in calls))
        track = next(body for method, path, body in calls if method == "PUT")["releases"][0]
        self.assertEqual("draft", track["status"])
        self.assertTrue(calls[-1][1].endswith(":commit?changesNotSentForReview=false"))


class ArgumentsTest(unittest.TestCase):

    POSITIONAL = ["com.example", "app.apk", "mapping.txt", "5.1.0 (510)", "3", "notes.txt"]

    def test_the_flags_read_in_any_order(self):
        self.assertEqual([*self.POSITIONAL, True, False, "store"], play_store_release.arguments_of(
            [*self.POSITIONAL, "--screenshots", "store", "--no-listings", "--draft"]))
        self.assertEqual([*self.POSITIONAL, False, True, None], play_store_release.arguments_of(self.POSITIONAL))

    def test_a_misread_command_line_is_refused(self):
        for flags in [["--screenshots"], ["--screenshots", "--draft"], ["--draft", "--draft"], ["--images", "x"]]:
            self.assertIsNone(play_store_release.arguments_of([*self.POSITIONAL, *flags]), flags)
        self.assertIsNone(play_store_release.arguments_of([*self.POSITIONAL[:4], "6", "notes.txt"]))


if __name__ == "__main__":
    unittest.main()
