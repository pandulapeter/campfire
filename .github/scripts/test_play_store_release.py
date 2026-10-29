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

    def run_main(self, locales, *, draft=False, listings=True):
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
            calls.append(("UPLOAD", path, None))
            return {"versionCode": 7}

        with tempfile.TemporaryDirectory() as folder:
            paths = {name: os.path.join(folder, name) for name in ["notes.txt", "app.apk", "mapping.txt"]}
            for name, path in paths.items():
                with open(path, "w", encoding="utf-8") as file:
                    file.write("- New\n" if name == "notes.txt" else "x")
            with mock.patch.object(play_store_release, "request", request), \
                    mock.patch.object(play_store_release, "upload", upload), \
                    mock.patch.object(store_listings, "app_languages", return_value=["de", "en"]), \
                    mock.patch.object(store_listings, "read", return_value=LISTINGS), \
                    mock.patch.dict(os.environ, {"RELEASE_NOTES_BY_LANGUAGE": '{"de": "- Neu"}'}), \
                    mock.patch("builtins.print"):
                try:
                    play_store_release.main("com.example", paths["app.apk"], paths["mapping.txt"], "5.1.0 (510)", "3",
                                            paths["notes.txt"], draft, listings)
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

    def test_an_app_language_without_a_listing_fails_before_anything_is_uploaded(self):
        calls = self.run_main(["en-US"])
        self.assertFalse(any(method in {"UPLOAD", "PUT", "PATCH"} or path.endswith(":commit") for method, path, _ in calls))

    def test_listings_off_and_a_draft_write_only_the_notes(self):
        calls = self.run_main(["en-US"], draft=True, listings=False)
        self.assertFalse(any(method == "PATCH" for method, _, _ in calls))
        track = next(body for method, path, body in calls if method == "PUT")["releases"][0]
        self.assertEqual("draft", track["status"])
        self.assertTrue(calls[-1][1].endswith(":commit?changesNotSentForReview=false"))


if __name__ == "__main__":
    unittest.main()
