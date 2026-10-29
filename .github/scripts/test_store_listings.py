# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import store_listings
from store_listings import app_languages, changes, check, language, match_locales, notes_by_language, read

# Each store's codes for the app's languages and the regional copies a console offers beside them, as their
# documentation lists them: Play (and its Hebrew iw-IL), App Store Connect, and Partner Center, which writes them in
# lowercase.
STORE_LOCALES = {
    "google-play": ["en-US", "en-GB", "de-DE", "es-ES", "es-419", "es-US", "fr-FR", "fr-CA", "hu-HU", "it-IT", "ro", "iw-IL"],
    "app-store": ["en-US", "en-GB", "de-DE", "es-ES", "es-MX", "fr-FR", "fr-CA", "hu", "it", "ro", "he"],
    "microsoft-store": ["en-us", "en-gb", "de-de", "es-es", "es-mx", "fr-fr", "fr-ca", "hu-hu", "it-it", "ro-ro", "he-il"],
}


def block(text):
    return f"```\n{text}\n```\n"


class LanguageTest(unittest.TestCase):

    def test_every_store_code_of_the_apps_languages_is_read_as_the_language(self):
        for store, locales in STORE_LOCALES.items():
            matched, missing, unknown = match_locales(locales, app_languages())
            self.assertEqual([], missing, store)
            self.assertEqual([], unknown, store)

    def test_a_regional_copy_gets_its_language(self):
        matched, missing, unknown = match_locales(["fr-FR", "fr-CA", "ro", "ja-JP"], ["fr", "ro", "de"])
        self.assertEqual({"fr-FR": "fr", "fr-CA": "fr", "ro": "ro"}, matched)
        self.assertEqual(["de"], missing)
        self.assertEqual(["ja-JP"], unknown)


class ReadTest(unittest.TestCase):

    def write(self, folder, store, language, text):
        (folder / store).mkdir(exist_ok=True)
        (folder / store / f"{language}.md").write_text(text, encoding="utf-8")

    def test_fields_are_keyed_by_the_api_and_english_only_ones_come_from_english(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name)
            self.write(folder, "app-store", "en", "## Name (30 characters max)\n" + block("Campfire")
                       + "## Support URL\n" + block("https://example.com/") + "## Copyright\n" + block("2026"))
            self.write(folder, "app-store", "de", "## Name (30 characters max)\n" + block("Lagerfeuer"))
            listings = read("app-store", folder)
        self.assertEqual("Lagerfeuer", listings["de"]["name"])
        self.assertEqual("https://example.com/", listings["de"]["supportUrl"])
        self.assertEqual("2026", listings["de"]["copyright"])

    def test_features_are_a_list_of_blocks_and_search_terms_a_list_split_at_commas(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name)
            self.write(folder, "microsoft-store", "en", "## Product features (20 max, 200 characters each)\n"
                       + block("One") + block("Two") + "## Search terms (7 max, 30 characters each, 21 words in all)\n"
                       + block("chords, guitar tuner"))
            listing = read("microsoft-store", folder)["en"]
        self.assertEqual(["One", "Two"], listing["features"])
        self.assertEqual(["chords", "guitar tuner"], listing["keywords"])

    def test_the_repository_listings_read(self):
        for store in store_listings.STORES:
            listings = read(store)
            self.assertEqual(app_languages(), sorted(listings))
            self.assertTrue(all(listings["de"].values()), store)


class CheckTest(unittest.TestCase):

    def test_the_repository_listings_pass(self):
        self.assertEqual([], check())

    def test_an_unknown_heading_and_a_missing_field_fail(self):
        with tempfile.TemporaryDirectory() as name:
            folder, resources = Path(name), Path(name) / "resources"
            (resources / "values").mkdir(parents=True)
            for store in store_listings.STORES:
                (folder / store).mkdir()
                fields = "".join(f"## {field}\n" + block("text") for field in store_listings.FIELDS[store])
                (folder / store / "en.md").write_text(fields, encoding="utf-8")
            (folder / "google-play/en.md").write_text("## App name (30 characters max)\n" + block("x"), encoding="utf-8")
            errors = check(folder, resources)
        self.assertIn("google-play/en.md: \"App name\" is neither written by store_listings.py's FIELDS nor listed as MANUAL", errors)
        self.assertIn("google-play/en.md: has no \"App title\", which the release writes", errors)
        self.assertEqual(4, len(errors))

    def test_a_language_the_app_lacks_fails(self):
        with tempfile.TemporaryDirectory() as name:
            folder, resources = Path(name), Path(name) / "resources"
            (resources / "values").mkdir(parents=True)
            for store in store_listings.STORES:
                (folder / store).mkdir()
                for code in ["en", "de"]:
                    fields = "".join(f"## {field}\n" + block("text") for field in store_listings.FIELDS[store])
                    (folder / store / f"{code}.md").write_text(fields, encoding="utf-8")
            errors = check(folder, resources)
        self.assertEqual(["google-play: has de, en, and the app has en"], errors[:1])


class ChangesTest(unittest.TestCase):

    def test_names_the_changed_fields_with_their_lengths(self):
        line = changes("de-DE", {"title": "Alt", "keywords": ["a"]}, {"title": "Neuer", "keywords": ["a"]})
        self.assertEqual("de-DE: title 3 -> 5.", line)
        self.assertEqual("de-DE: nothing changed.", changes("de-DE", {"title": "x"}, {"title": "x"}))


class NotesByLanguageTest(unittest.TestCase):

    def test_a_language_without_notes_and_a_hand_dispatch_get_the_english(self):
        with mock.patch.dict(os.environ, {"RELEASE_NOTES_BY_LANGUAGE": '{"de": "- Neu"}'}):
            notes = notes_by_language("- New")
            self.assertEqual("- Neu", notes("de"))
            self.assertEqual("- New", notes("fr"))
        with mock.patch.dict(os.environ, {"RELEASE_NOTES_BY_LANGUAGE": ""}):
            self.assertEqual("- New", notes_by_language("- New")("de"))


if __name__ == "__main__":
    unittest.main()
