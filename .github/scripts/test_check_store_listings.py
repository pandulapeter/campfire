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

from check_store_listings import field_errors, listing_errors


def block(text):
    return f"```\n{text}\n```\n"


class CheckStoreListingsTest(unittest.TestCase):

    def test_a_field_within_its_limit_passes(self):
        listing = "## Description (4,000 characters max)\n\n" + block("a" * 4000)
        self.assertEqual([], field_errors("store.md", listing))

    def test_a_field_one_character_over_its_limit_fails(self):
        listing = "## Description (4,000 characters max)\n\n" + block("a" * 4001)
        self.assertEqual(["store.md: Description is 4001 characters, 4000 at most"], field_errors("store.md", listing))

    def test_bytes_are_counted_for_a_bytes_limit(self):
        listing = "## Keywords (100 bytes max)\n\n" + block("é" + "a" * 99)
        self.assertEqual(["store.md: Keywords is 101 bytes, 100 at most"], field_errors("store.md", listing))

    def test_each_block_is_counted_for_a_per_item_limit_and_the_blocks_are_counted_too(self):
        within = "## Product features (2 max, 5 characters each)\n\n" + block("abcde") + block("abc")
        self.assertEqual([], field_errors("store.md", within))
        over = "## Product features (2 max, 5 characters each)\n\n" + block("abcdef") + block("abc") + block("ab")
        self.assertEqual(
            [
                "store.md: Product features has 3 items, 2 at most",
                "store.md: Product features item 1 is 6 characters, 5 at most",
            ],
            field_errors("store.md", over),
        )

    def test_a_heading_without_a_limit_is_ignored(self):
        listing = "## Support URL\n\n" + block("a" * 5000) + "\n## Search terms (7 max)\n\n" + block("a" * 5000)
        self.assertEqual([], field_errors("store.md", listing))

    def test_a_field_with_a_limit_and_no_text_fails(self):
        listing = "## Subtitle (30 characters max)\n\nNothing yet.\n\n## Support URL\n\n" + block("https://example.com")
        self.assertEqual(["store.md: Subtitle has no text under its heading"], field_errors("store.md", listing))

    def test_only_the_blocks_under_a_heading_count_towards_it(self):
        listing = "## Name (5 characters max)\n\n" + block("abcde") + "\n## Notes\n\n" + block("a" * 50)
        self.assertEqual([], field_errors("store.md", listing))

    def test_a_translation_in_a_language_folder_is_judged_and_named_by_its_folder(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "de").mkdir()
            (Path(folder) / "de/google-play.md").write_text("## Title (5 characters max)\n" + block("Lagerfeuer"), encoding="utf-8")
            self.assertEqual(["de/google-play.md: Title is 10 characters, 5 at most"], listing_errors(Path(folder)))

    def test_the_repository_listings_are_within_their_limits(self):
        self.assertEqual([], listing_errors())


if __name__ == "__main__":
    unittest.main()
