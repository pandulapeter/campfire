# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import contextlib
import io
import unittest

from require_secrets import main


def run(arguments, environment):
    errors = io.StringIO()
    with contextlib.redirect_stderr(errors):
        code = main(arguments, environment)
    return code, errors.getvalue().splitlines()


class RequireSecretsTest(unittest.TestCase):

    def test_one_empty_and_one_set_secret_fail_naming_the_empty_one(self):
        code, lines = run(["DROPBOX_APP_KEY", "ASC_KEY_ID"], {"DROPBOX_APP_KEY": "", "ASC_KEY_ID": "ABCDEFGHIJ"})
        self.assertEqual(code, 1)
        self.assertEqual(lines, ["::error::DROPBOX_APP_KEY is empty or not visible to this workflow."])

    def test_an_unset_secret_is_empty(self):
        code, lines = run(["DROPBOX_APP_KEY"], {})
        self.assertEqual(code, 1)
        self.assertEqual(len(lines), 1)

    def test_set_secrets_pass_silently(self):
        self.assertEqual(run(["DROPBOX_APP_KEY"], {"DROPBOX_APP_KEY": "key"}), (0, []))

    def test_the_consequence_follows_the_error(self):
        _, lines = run(["--why", "The published app would have no sync provider at all.", "DROPBOX_APP_KEY"], {})
        self.assertEqual(
            lines,
            [
                "::error::DROPBOX_APP_KEY is empty or not visible to this workflow. "
                "The published app would have no sync provider at all."
            ],
        )


if __name__ == "__main__":
    unittest.main()
