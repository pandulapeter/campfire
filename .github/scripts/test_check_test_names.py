# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from check_test_names import findings


def lines(source):
    return [line for line, _ in findings(source)]


class FindingsTest(unittest.TestCase):

    def test_a_test_name_with_a_comma_is_reported(self):
        self.assertEqual([2], lines('@Test\nfun `a file is read, whatever its case`() = Unit'))

    def test_a_name_without_a_comma_is_accepted(self):
        self.assertEqual([], lines("@Test\nfun `a file is read in any case - and kept`() = Unit"))

    def test_commas_outside_a_function_name_are_accepted(self):
        self.assertEqual([], lines('val text = "a, b"\n/** Reads `a, b`. */\nfun read(a: Int, b: Int) = a + b'))


if __name__ == "__main__":
    unittest.main()
