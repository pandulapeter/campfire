# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import tempfile
import unittest
from pathlib import Path

from PIL import Image

from contact_sheets import THUMBNAIL_HEIGHT, main, store_rows


def write(path, size, format="PNG"):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (200, 100, 50)).save(path, format)


class ContactSheetsTest(unittest.TestCase):

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.folder = Path(self.directory.name)

    def tearDown(self):
        self.directory.cleanup()

    def test_a_store_row_is_a_device_with_a_row_per_language_in_the_listings_order(self):
        for language in ["en", "hu"]:
            for number in [1, 2, 10]:
                write(self.folder / f"store/app-store/{language}/iphone-{number}.png", (66, 143))
            write(self.folder / f"store/app-store/{language}/iphone-duo-1.png", (143, 100))
            write(self.folder / f"store/app-store/{language}/header.png", (400, 100))
        rows = store_rows(self.folder / "store/app-store")
        self.assertEqual(["iphone", "iphone-duo", "header"], sorted(rows, key=["iphone", "iphone-duo", "header"].index))
        self.assertEqual(["en", "hu"], [language for language, _ in rows["iphone"]])
        self.assertEqual(["iphone-1.png", "iphone-2.png", "iphone-10.png"], [path.name for path in rows["iphone"][0][1]])

    def test_writes_a_sheet_per_store_row_and_one_of_the_website(self):
        write(self.folder / "store/google-play/en/phone-1.png", (72, 128))
        write(self.folder / "store/google-play/en/feature-graphic.png", (1024, 500))
        write(self.folder / "store/google-play/icon.png", (512, 512))
        write(self.folder / "website/assets/screenshots/phone-songs-dark.webp", (45, 98), "WEBP")
        write(self.folder / "website/assets/screenshots/hu/phone-songs-dark.webp", (45, 98), "WEBP")
        main(self.folder / "store", self.folder / "website", self.folder / "sheets")
        sheets = sorted(path.name for path in (self.folder / "sheets").iterdir())
        self.assertEqual(["google-play-feature-graphic.jpg", "google-play-phone.jpg", "website.jpg"], sheets)
        with Image.open(self.folder / "sheets/website.jpg") as website:
            self.assertGreater(website.height, 2 * THUMBNAIL_HEIGHT)


if __name__ == "__main__":
    unittest.main()
