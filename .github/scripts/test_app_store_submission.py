# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import struct
import tempfile
import unittest
import zlib
from pathlib import Path
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

    def run_listing(self, platform="IOS", locales=("en-US", "de-DE", "de-AT"), info_locales=None,
                    info_state="PREPARE_FOR_SUBMISSION", has_earlier=True, write_listings=True, refuse=None,
                    privacy_pages=()):
        calls = []
        info_locales = locales if info_locales is None else info_locales

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
                return {"data": [{"id": f"i-{locale}", "attributes": {"locale": locale, "privacyPolicyUrl": "https://p/en/"}}
                                 for locale in info_locales]}
            if path.endswith("/appStoreReviewDetail"):
                return None
            if path.startswith("/appStoreVersions/version?"):
                return {"data": {"attributes": {"copyright": "2025"}}}
            return None

        def read(store):
            if store == "mac-app-store":
                return {code: {field: text for field, text in listing.items() if field not in {"name", "subtitle", "supportUrl", "marketingUrl"}}
                        for code, listing in LISTINGS.items()}
            return LISTINGS

        notes = {"de": "- Neu", "en": "- New"}
        with mock.patch.object(app_store_submission, "request", request), \
                mock.patch.object(app_store_submission, "page_exists", lambda url: url in privacy_pages), \
                mock.patch.object(store_listings, "app_languages", return_value=["de", "en"]), \
                mock.patch.object(store_listings, "read", read), \
                mock.patch("builtins.print"):
            try:
                app_store_submission.write_listing("app", platform, {"id": "version"}, notes.get, has_earlier, write_listings)
                failed = False
            except SystemExit:
                failed = True
        patches = {path: body["data"]["attributes"] for method, path, body in calls if method == "PATCH" and body}
        posts = {}
        for method, path, body in calls:
            if method == "POST" and body:
                posts.setdefault(path, []).append(body["data"])
        return patches, calls, failed, posts

    def test_every_localization_gets_its_languages_text_and_notes(self):
        patches, calls, failed, _ = self.run_listing()
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

    def test_the_mac_never_writes_the_name_and_subtitle(self):
        patches, calls, failed, posts = self.run_listing(platform="MAC_OS")
        self.assertFalse(failed)
        self.assertFalse(any(path.startswith("/appInfoLocalizations") for path in patches))
        self.assertNotIn("/appInfoLocalizations", posts)

    def test_a_missing_language_gets_both_localizations(self):
        patches, calls, failed, posts = self.run_listing(
            locales=("en-US",), privacy_pages=("https://campfire-songbook.com/de/privacy/",),
        )
        self.assertFalse(failed)
        [version_localization] = posts["/appStoreVersionLocalizations"]
        self.assertEqual({
            "locale": "de-DE", "description": "Description de", "keywords": "keywords,de", "promotionalText": "Promo de",
            "supportUrl": "https://s/", "marketingUrl": "https://m/", "whatsNew": "- Neu",
        }, version_localization["attributes"])
        self.assertEqual("version", version_localization["relationships"]["appStoreVersion"]["data"]["id"])
        [info_localization] = posts["/appInfoLocalizations"]
        self.assertEqual({
            "locale": "de-DE", "name": "Campfire de", "subtitle": "Sub de",
            "privacyPolicyUrl": "https://campfire-songbook.com/de/privacy/",
        }, info_localization["attributes"])
        self.assertEqual("next", info_localization["relationships"]["appInfo"]["data"]["id"])
        # Created, then only the existing ones written.
        self.assertNotIn("/appStoreVersionLocalizations/v-de-DE", patches)
        self.assertIn("/appStoreVersionLocalizations/v-en-US", patches)

    def test_a_first_version_creates_a_language_without_whats_new(self):
        _, _, _, posts = self.run_listing(locales=("en-US",), has_earlier=False)
        self.assertNotIn("whatsNew", posts["/appStoreVersionLocalizations"][0]["attributes"])

    def test_a_language_the_website_lacks_takes_the_english_privacy_policy(self):
        _, _, _, posts = self.run_listing(locales=("en-US",))
        self.assertEqual("https://p/en/", posts["/appInfoLocalizations"][0]["attributes"]["privacyPolicyUrl"])

    def test_an_existing_app_info_localization_is_not_created_again(self):
        _, _, failed, posts = self.run_listing(locales=("en-US",), info_locales=("en-US", "de-DE"))
        self.assertFalse(failed)
        self.assertNotIn("/appInfoLocalizations", posts)
        self.assertEqual(1, len(posts["/appStoreVersionLocalizations"]))

    def test_the_mac_creates_a_language_with_the_ios_urls_and_name(self):
        _, _, failed, posts = self.run_listing(platform="MAC_OS", locales=("en-US",), info_locales=("en-US",))
        self.assertFalse(failed)
        attributes = posts["/appStoreVersionLocalizations"][0]["attributes"]
        self.assertEqual(("https://s/", "https://m/"), (attributes["supportUrl"], attributes["marketingUrl"]))
        self.assertEqual("Description de", attributes["description"])
        info = posts["/appInfoLocalizations"][0]["attributes"]
        self.assertEqual(("Campfire de", "Sub de"), (info["name"], info["subtitle"]))

    def test_listings_off_writes_only_the_notes_and_skips_a_missing_localization(self):
        patches, calls, failed, posts = self.run_listing(locales=("en-US",), write_listings=False)
        self.assertFalse(failed)
        self.assertEqual({"/appStoreVersionLocalizations/v-en-US": {"whatsNew": "- New"}}, patches)
        self.assertEqual({}, posts)

    def test_a_first_version_gets_no_whats_new(self):
        patches, _, _, _ = self.run_listing(has_earlier=False)
        self.assertNotIn("whatsNew", patches["/appStoreVersionLocalizations/v-en-US"])

    def test_no_editable_app_info_leaves_the_name_for_the_next_version(self):
        patches, _, failed, posts = self.run_listing(locales=("en-US",), info_state="WAITING_FOR_REVIEW")
        self.assertFalse(failed)
        self.assertFalse(any(path.startswith("/appInfoLocalizations") for path in patches))
        self.assertNotIn("/appInfoLocalizations", posts)
        self.assertIn("/appStoreVersionLocalizations", posts)

    def test_a_name_apple_refuses_fails_the_run(self):
        # write_listing runs before submit in main, so the version is never submitted with the old name.
        _, _, failed, _ = self.run_listing(refuse="/appInfoLocalizations")
        self.assertTrue(failed)


def png(path, width=2, height=1):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    raw = b"".join(b"\x00" + b"\x00\x00\x00" * width for _ in range(height))
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class ReplaceScreenshotsTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.folder = Path(directory.name)
        for code, names in {"en": ["iphone-1.png", "iphone-2.png", "ipad-1.png"], "de": ["iphone-1.png"]}.items():
            (self.folder / "app-store" / code).mkdir(parents=True)
            for name in names:
                png(self.folder / "app-store" / code / name)

    def run_screenshots(self, delivery=lambda item, polls: "COMPLETE"):
        calls, parts, polls = [], [], {}
        reserved = iter(range(1, 100))

        def request(method, path, body=None, missing_ok=False, conflict_ok=False):
            calls.append((method, path, body))
            if path.endswith("/appStoreVersionLocalizations"):
                return {"data": [{"id": f"v-{locale}", "attributes": {"locale": locale}} for locale in ("en-US", "de-DE", "fr-FR")]}
            if path == "/appStoreVersionLocalizations/v-en-US/appScreenshotSets":
                return {"data": [{"id": "set-iphone", "attributes": {"screenshotDisplayType": "APP_IPHONE_67"}},
                                 {"id": "set-watch", "attributes": {"screenshotDisplayType": "APP_WATCH_ULTRA"}}]}
            if method == "POST" and path == "/appScreenshotSets":
                return {"data": {"id": f"new-{body['data']['attributes']['screenshotDisplayType']}"}}
            if path.endswith("/appScreenshotSets"):
                return {"data": []}
            if method == "GET" and path == "/appScreenshotSets/set-iphone/appScreenshots":
                return {"data": [{"id": "old-1"}, {"id": "old-2"}]}
            if method == "GET" and path.endswith("/appScreenshots"):
                return {"data": []}
            if method == "POST" and path == "/appScreenshots":
                return {"data": {"id": f"s{next(reserved)}", "attributes": {"uploadOperations": [
                    {"method": "PUT", "url": "https://upload/a", "offset": 0, "length": 10, "requestHeaders": []},
                    {"method": "PUT", "url": "https://upload/b", "offset": 10, "length": 1000, "requestHeaders": []},
                ]}}}
            if method == "GET" and path.startswith("/appScreenshots/"):
                item = path.split("/")[2].split("?")[0]
                polls[item] = polls.get(item, 0) + 1
                return {"data": {"attributes": {"fileName": "iphone-1.png", "assetDeliveryState": {
                    "state": delivery(item, polls[item]), "errors": [{"description": "Bad image"}],
                }}}}
            return None

        with mock.patch.object(app_store_submission, "request", request), \
                mock.patch.object(app_store_submission, "upload_part", lambda operation, data, name: parts.append((operation["url"], len(data)))), \
                mock.patch.object(store_listings, "app_languages", return_value=["de", "en"]), \
                mock.patch.object(app_store_submission.time, "sleep"), \
                mock.patch("builtins.print"):
            try:
                app_store_submission.replace_screenshots({"id": "version"}, "IOS", self.folder)
                failed = False
            except SystemExit:
                failed = True
        return calls, parts, failed

    def test_every_set_is_emptied_refilled_committed_and_ordered(self):
        calls, parts, failed = self.run_screenshots()
        self.assertFalse(failed)
        deleted = [path for method, path, _ in calls if method == "DELETE"]
        self.assertEqual(["/appScreenshots/old-1", "/appScreenshots/old-2"], deleted)
        created = [body["data"]["attributes"]["screenshotDisplayType"] for method, path, body in calls
                   if method == "POST" and path == "/appScreenshotSets"]
        # en has its iPhone set already, and the iPad set made; de has only iPhone images, its iPad set left alone.
        self.assertEqual(["APP_IPAD_PRO_3GEN_129", "APP_IPHONE_67"], created)
        reservations = [body["data"] for method, path, body in calls if method == "POST" and path == "/appScreenshots"]
        self.assertEqual(4, len(reservations))
        first = self.folder / "app-store/en/iphone-1.png"
        self.assertEqual({"fileName": "iphone-1.png", "fileSize": first.stat().st_size}, reservations[0]["attributes"])
        self.assertEqual("set-iphone", reservations[0]["relationships"]["appScreenshotSet"]["data"]["id"])
        self.assertEqual(8, len(parts))
        commits = {path: body["data"]["attributes"] for method, path, body in calls
                   if method == "PATCH" and path.startswith("/appScreenshots/")}
        self.assertEqual({"uploaded": True, "sourceFileChecksum": app_store_submission.hashlib.md5(first.read_bytes()).hexdigest()},
                         commits["/appScreenshots/s1"])
        orders = {path: [item["id"] for item in body["data"]] for method, path, body in calls
                  if method == "PATCH" and path.endswith("/relationships/appScreenshots")}
        self.assertEqual(["s1", "s2"], orders["/appScreenshotSets/set-iphone/relationships/appScreenshots"])
        # The watch set has no images in the run, and fr-FR is a language the app lacks: neither is touched.
        self.assertFalse(any("set-watch" in path or "v-fr-FR" in path for _, path, _ in calls))

    def test_waits_until_every_screenshot_is_processed(self):
        _, _, failed = self.run_screenshots(lambda item, polls: "COMPLETE" if polls > 2 else "UPLOAD_COMPLETE")
        self.assertFalse(failed)

    def test_a_failed_delivery_fails_the_run(self):
        _, _, failed = self.run_screenshots(lambda item, polls: "FAILED" if item == "s2" else "COMPLETE")
        self.assertTrue(failed)

    def test_still_processing_at_the_timeout_fails_the_run(self):
        with mock.patch.object(app_store_submission, "SCREENSHOT_TIMEOUT_SECONDS", -1):
            _, _, failed = self.run_screenshots(lambda item, polls: "UPLOAD_COMPLETE")
        self.assertTrue(failed)


class MainTest(unittest.TestCase):

    def run_main(self, screenshots, delivery="COMPLETE", write_listings=True):
        calls = []
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as notes:
            notes.write("- New")
        self.addCleanup(Path(notes.name).unlink)

        def replace(version, platform, folder):
            calls.append("screenshots")
            if delivery == "FAILED":
                raise SystemExit(1)

        with mock.patch.object(app_store_submission, "find_app", return_value={"id": "app"}), \
                mock.patch.object(app_store_submission, "awaiting_versions", return_value=[]), \
                mock.patch.object(app_store_submission, "wait_for_build", return_value={"id": "build"}), \
                mock.patch.object(app_store_submission, "version_for_release",
                                  return_value=({"id": "version", "attributes": {"appStoreState": "PREPARE_FOR_SUBMISSION"}}, True)), \
                mock.patch.object(app_store_submission, "request"), \
                mock.patch.object(app_store_submission, "write_listing"), \
                mock.patch.object(app_store_submission, "replace_screenshots", replace), \
                mock.patch.object(app_store_submission, "submit", lambda *_: calls.append("submit")), \
                mock.patch.object(store_listings, "app_languages", return_value=["en"]), \
                mock.patch("builtins.print"):
            try:
                app_store_submission.main("id", "IOS", "1.0.0", "7", notes.name, False, write_listings, screenshots)
            except SystemExit:
                pass
        return calls

    def test_screenshots_go_in_before_the_submission(self):
        self.assertEqual(["screenshots", "submit"], self.run_main("store"))

    def test_a_failed_delivery_stops_before_the_submission(self):
        self.assertEqual(["screenshots"], self.run_main("store", delivery="FAILED"))

    def test_no_flag_touches_no_screenshot(self):
        self.assertEqual(["submit"], self.run_main(None))

    def test_listings_left_as_they_are_keep_their_screenshots(self):
        self.assertEqual(["submit"], self.run_main("store", write_listings=False))

    def test_the_usage_line_reads_the_folder(self):
        parse = app_store_submission.parse_arguments
        self.assertEqual(["b", "IOS", "1", "2", "n", True, False, "store"],
                         parse(["b", "IOS", "1", "2", "n", "--screenshots", "store", "--prepare", "--no-listings"]))
        self.assertIsNone(parse(["b", "IOS", "1", "2", "n", "--screenshots"]))
        self.assertIsNone(parse(["b", "IOS", "1", "2", "n", "--screenshots", "--prepare"]))
        self.assertEqual(["b", "MAC_OS", "1", "2", "n", False, True, None], parse(["b", "MAC_OS", "1", "2", "n"]))


if __name__ == "__main__":
    unittest.main()
