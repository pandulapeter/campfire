# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import contextlib
import io
import os
import tempfile
import unittest

from write_local_properties import main


def read_properties(text):
    """What java.util.Properties makes of the lines this script writes: a backslash escapes the character after it."""
    values = {}
    for line in text.splitlines():
        key, _, raw = line.partition("=")
        value, index = [], 0
        while index < len(raw):
            if raw[index] == "\\" and index + 1 < len(raw):
                index += 1
            value.append(raw[index])
            index += 1
        values[key] = "".join(value)
    return values


class WriteLocalPropertiesTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = os.path.join(directory.name, "local.properties")

    def write(self, arguments, environment):
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            code = main(arguments, environment, self.path)
        return code, errors.getvalue()

    def read_bytes(self):
        with open(self.path, "rb") as file:
            return file.read()

    def test_a_backslash_is_doubled(self):
        self.assertEqual(self.write(["campfire.dropbox.appKey=KEY"], {"KEY": "a\\b"})[0], 0)
        self.assertEqual(self.read_bytes(), b"campfire.dropbox.appKey=a\\\\b\n")

    def test_every_character_a_shell_would_read_survives(self):
        secret = "a\\b $HOME `id` \"quoted\" 'single' é"
        self.write(["campfire.android.keyPassword=PASSWORD"], {"PASSWORD": secret})
        text = self.read_bytes().decode("utf-8")
        self.assertEqual(read_properties(text), {"campfire.android.keyPassword": secret})
        self.assertIn("é".encode("utf-8"), self.read_bytes())

    def test_append_keeps_the_lines_already_there(self):
        self.write(["campfire.dropbox.appKey=KEY"], {"KEY": "key"})
        self.write(["--append", "campfire.buildNumber=:42"], {})
        self.assertEqual(self.read_bytes(), b"campfire.dropbox.appKey=key\ncampfire.buildNumber=42\n")

    def test_without_append_the_file_is_replaced(self):
        self.write(["campfire.dropbox.appKey=KEY"], {"KEY": "old"})
        self.write(["campfire.dropbox.appKey=KEY"], {"KEY": "new"})
        self.assertEqual(self.read_bytes(), b"campfire.dropbox.appKey=new\n")

    def test_a_literal_is_written_as_it_is(self):
        self.write(["campfire.android.keystoreFile=:release.keystore"], {})
        self.assertEqual(self.read_bytes(), b"campfire.android.keystoreFile=release.keystore\n")

    def test_an_unset_variable_is_an_error_and_writes_nothing(self):
        code, errors = self.write(["campfire.dropbox.appKey=KEY"], {})
        self.assertEqual(code, 1)
        self.assertIn("::error::KEY", errors)
        self.assertFalse(os.path.exists(self.path))

    def test_an_empty_variable_is_written_empty(self):
        self.write(["campfire.dropbox.appKey=KEY"], {"KEY": ""})
        self.assertEqual(self.read_bytes(), b"campfire.dropbox.appKey=\n")


if __name__ == "__main__":
    unittest.main()
