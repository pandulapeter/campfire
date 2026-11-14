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

from store_images import check, images, manual_summary, png_info


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def write_png(path, width, height, color_type=2, transparency=False):
    """A PNG's header and an empty image: only the header is read, so the pixels are never needed."""
    path.parent.mkdir(parents=True, exist_ok=True)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0))
    if transparency:
        data += chunk(b"tRNS", b"\x00\x00\x00\x00\x00\x00")
    path.write_bytes(data + chunk(b"IDAT", zlib.compress(b"")) + chunk(b"IEND", b""))


def framed(folder, languages=("en",)):
    """Every image the framing writes, at the sizes it writes them, in each language."""
    for code in languages:
        for number in range(1, 9):
            write_png(folder / f"google-play/{code}/phone-{number}.png", 1440, 2560)
            write_png(folder / f"google-play/{code}/small-tablet-{number}.png", 1200, 1920)
            write_png(folder / f"google-play/{code}/large-tablet-{number}.png", 2752, 2064)
            write_png(folder / f"google-play/{code}/chromebook-{number}.png", 1920, 1080)
            write_png(folder / f"app-store/{code}/iphone-{number}.png", 1320, 2868)
            write_png(folder / f"app-store/{code}/iphone-duo-{number}.png", 2853, 2007)
            write_png(folder / f"app-store/{code}/ipad-{number}.png", 2752, 2064)
            write_png(folder / f"mac-app-store/{code}/mac-{number}.png", 2880, 1800)
            write_png(folder / f"microsoft-store/{code}/windows-{number}.png", 2880, 1800)
        write_png(folder / f"google-play/{code}/feature-graphic.png", 1024, 500)
        write_png(folder / f"app-store/{code}/header.png", 4320, 1080)
        write_png(folder / f"app-store/{code}/search-results.png", 1320, 2868)
    write_png(folder / "google-play/icon.png", 512, 512, color_type=6)
    write_png(folder / "microsoft-store/poster-art.png", 720, 1080)


class StoreImagesTest(unittest.TestCase):

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.folder = Path(self.directory.name)

    def tearDown(self):
        self.directory.cleanup()

    def test_what_the_framing_writes_passes_and_is_counted_per_set(self):
        framed(self.folder, ["en", "hu"])
        errors, counts = check(self.folder, ["en", "hu"])
        self.assertEqual([], errors)
        self.assertEqual({"phoneScreenshots": 16, "sevenInchScreenshots": 16, "tenInchScreenshots": 16, "featureGraphic": 2},
                         counts["google-play"])
        self.assertEqual({"APP_IPHONE_67": 16, "APP_IPAD_PRO_3GEN_129": 16}, counts["app-store"])

    def test_sets_are_in_the_listings_order_whatever_the_file_names_sort_as(self):
        framed(self.folder)
        found = images(self.folder, "google-play", "en")
        self.assertEqual(["phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots", "featureGraphic"], list(found))
        write_png(self.folder / "app-store/en/iphone-10.png", 1320, 2868)
        self.assertEqual([f"iphone-{n}.png" for n in [*range(1, 9), 10]],
                         [path.name for path in images(self.folder, "app-store", "en")["APP_IPHONE_67"]])

    def test_a_language_the_app_has_and_a_store_lacks_fails(self):
        framed(self.folder, ["en"])
        errors, _ = check(self.folder, ["en", "hu"])
        self.assertIn("google-play: has no images in hu", errors)
        self.assertIn("microsoft-store: has no images in hu", errors)

    def test_an_image_no_set_and_no_manual_entry_names_fails(self):
        framed(self.folder)
        write_png(self.folder / "app-store/en/vision-1.png", 3840, 2160)
        errors, _ = check(self.folder, ["en"])
        self.assertEqual(["app-store/en/vision-1.png: is neither in store_images.py's SETS nor in its MANUAL"], errors)

    def test_a_size_the_display_type_does_not_take_fails(self):
        framed(self.folder)
        write_png(self.folder / "app-store/en/iphone-2.png", 1320, 2867)
        errors, _ = check(self.folder, ["en"])
        self.assertEqual(1, len(errors))
        self.assertTrue(errors[0].startswith("app-store/en/iphone-2.png: is 1320 x 2867"), errors)

    def test_a_phone_screenshot_longer_than_twice_its_width_fails_on_play(self):
        framed(self.folder)
        write_png(self.folder / "google-play/en/phone-1.png", 1080, 2424)
        errors, _ = check(self.folder, ["en"])
        self.assertEqual(["google-play/en/phone-1.png: is 1080 x 2424, its long side more than 2 times the short"], errors)

    def test_an_alpha_channel_fails_whether_rgba_or_transparency(self):
        framed(self.folder)
        write_png(self.folder / "mac-app-store/en/mac-1.png", 2880, 1800, color_type=6)
        write_png(self.folder / "mac-app-store/en/mac-2.png", 2880, 1800, transparency=True)
        errors, _ = check(self.folder, ["en"])
        self.assertEqual(["mac-app-store/en/mac-1.png: has an alpha channel, which the store refuses",
                          "mac-app-store/en/mac-2.png: has an alpha channel, which the store refuses"], errors)

    def test_too_many_and_too_few_fail(self):
        framed(self.folder)
        for number in range(9, 12):
            write_png(self.folder / f"microsoft-store/en/windows-{number}.png", 2880, 1800)
        for number in range(2, 9):
            (self.folder / f"google-play/en/phone-{number}.png").unlink()
        errors, _ = check(self.folder, ["en"])
        self.assertEqual(["google-play/en: has 1 phoneScreenshots, and the store takes 2-8",
                          "microsoft-store/en: has 11 Screenshot, and the store takes 1-10"], errors)

    def test_the_images_left_by_hand_are_listed_once_each_with_where_they_are(self):
        framed(self.folder, ["en", "hu"])
        lines = [line for line in manual_summary(self.folder).splitlines() if line.startswith("- ")]
        self.assertIn("- `google-play/<language>/chromebook-<n>.png`: Play's API has no image type for Chromebook screenshots.", lines)
        self.assertIn("- `app-store/<language>/header.png`: the product page's header is not in App Store Connect's API.", lines)
        self.assertIn("- `microsoft-store/poster-art.png`: set once, in Partner Center.", lines)
        self.assertEqual(6, len(lines))

    def test_a_file_that_is_not_a_png_fails(self):
        framed(self.folder)
        (self.folder / "microsoft-store/en/windows-1.png").write_bytes(b"\xff\xd8\xff")
        self.assertIsNone(png_info(self.folder / "microsoft-store/en/windows-1.png"))
        errors, _ = check(self.folder, ["en"])
        self.assertEqual(["microsoft-store/en/windows-1.png: is not a PNG"], errors)


if __name__ == "__main__":
    unittest.main()
