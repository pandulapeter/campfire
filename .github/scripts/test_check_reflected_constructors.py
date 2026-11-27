# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from check_reflected_constructors import missing_constructors, reflected_classes

SOURCE = """
package com.pandulapeter.campfire.widget

class ShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Unit
}

internal class TurnPage() : androidx.glance.appwidget.action.ActionCallback

internal class RandomPicksWidget : GlanceAppWidget()
"""
SHUFFLE = "com.pandulapeter.campfire.widget.ShuffleAction"
TURN_PAGE = "com.pandulapeter.campfire.widget.TurnPage"


class ReflectedConstructorsTest(unittest.TestCase):

    def test_every_action_callback_is_found_by_its_full_name(self):
        self.assertEqual([SHUFFLE, TURN_PAGE], reflected_classes([SOURCE]))

    def test_a_class_kept_by_name_alone_is_reported(self):
        seeds = f"{SHUFFLE}\n{TURN_PAGE}\n{TURN_PAGE}: TurnPage()\n"
        self.assertEqual([SHUFFLE], missing_constructors([SHUFFLE, TURN_PAGE], seeds))

    def test_kept_constructors_pass(self):
        seeds = f"{SHUFFLE}\n{SHUFFLE}: ShuffleAction()\n"
        self.assertEqual([], missing_constructors([SHUFFLE], seeds))


if __name__ == "__main__":
    unittest.main()
