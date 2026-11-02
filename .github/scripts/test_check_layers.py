# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from check_layers import ROOT, problems, table_packages

MAIN = "presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/"
PACKAGES = table_packages() | {
    f"{ROOT}.ui.components.songCard",
    f"{ROOT}.ui.navigation",
    f"{ROOT}.ui.state.songs",
    f"{ROOT}.ui.dialogs",
    f"{ROOT}.ui.dialogs.forms",
    f"{ROOT}.ui.screens.songs",
    f"{ROOT}.ui.screens.setlists",
}


def source(package, *imports):
    lines = [f"package {ROOT}.{package}" if package else f"package {ROOT}", ""]
    lines += [f"import {ROOT}.{imported}" for imported in imports]
    return "\n".join(lines) + "\n"


class CheckLayersTest(unittest.TestCase):

    def test_an_import_of_the_same_or_a_lower_layer_passes(self):
        text = source("ui.state.songs", "ui.navigation.CampfireDestination", "ui.components.songCard.SongCard",
                      "ui.theme.CampfireTheme", "localization.stringResource", "resources.Res")
        self.assertEqual([], problems(MAIN + "ui/state/songs/SongList.kt", text, PACKAGES))

    def test_an_import_of_a_higher_layer_fails_naming_both_layers(self):
        text = source("ui.components.songCard", "ui.navigation.CampfireDestination")
        failures = problems(MAIN + "ui/components/songCard/SongCard.kt", text, PACKAGES)
        self.assertEqual(1, len(failures))
        self.assertIn("ui.navigation.CampfireDestination", failures[0])
        self.assertIn("components may not import app", failures[0])

    def test_a_screen_may_not_import_another_screen(self):
        text = source("ui.screens.songs", "ui.screens.setlists.SetlistRow")
        failures = problems(MAIN + "ui/screens/songs/SongList.kt", text, PACKAGES)
        self.assertEqual(1, len(failures))
        self.assertIn("the screen songs may not import the screen setlists", failures[0])

    def test_a_screen_may_import_the_screens_package_itself(self):
        text = source("ui.screens.songs", "ui.screens.rememberSongActions")
        self.assertEqual([], problems(MAIN + "ui/screens/songs/SongList.kt", text, PACKAGES))

    def test_a_dialog_subpackage_is_a_surface_and_the_dialogs_package_is_app(self):
        self.assertEqual([], problems(MAIN + "ui/dialogs/forms/Form.kt", source("ui.dialogs.forms", "ui.dialogs.DialogType"),
                                      PACKAGES))
        self.assertEqual(1, len(problems(MAIN + "ui/dialogs/Host.kt", source("ui.dialogs", "ui.dialogs.forms.Form"),
                                         PACKAGES)))

    def test_the_root_packages_match_exactly(self):
        self.assertEqual([], problems(MAIN + "ui/theme/Theme.kt", source("ui.theme", "CAMPFIRE_VERSION_NAME"), PACKAGES))
        self.assertEqual(1, len(problems(MAIN + "ui/theme/Theme.kt", source("ui.theme", "ui.CampfireApp"), PACKAGES)))

    def test_a_top_level_function_import_is_judged_by_its_package(self):
        text = source("ui.components.songCard", "ui.state.songs.songItemKey")
        failures = problems(MAIN + "ui/components/songCard/SongCard.kt", text, PACKAGES)
        self.assertEqual(1, len(failures))
        self.assertIn("components may not import app", failures[0])

    def test_the_view_model_rule_only_judges_the_view_model_while_on(self):
        text = source("ui.navigation", "ui.CampfireViewModel")
        self.assertEqual([], problems(MAIN + "ui/navigation/Navigator.kt", text, PACKAGES, view_model_rule=False))
        self.assertEqual(1, len(problems(MAIN + "ui/navigation/Navigator.kt", text, PACKAGES, view_model_rule=True)))

    def test_with_the_view_model_rule_on_a_destination_entry_file_takes_the_view_model(self):
        text = source("ui.screens.songs", "ui.CampfireViewModel")
        self.assertEqual([], problems(MAIN + "ui/screens/songs/SongsScreen.kt", text, PACKAGES, view_model_rule=True))
        self.assertEqual(1, len(problems(MAIN + "ui/screens/songs/SongList.kt", text, PACKAGES, view_model_rule=True)))

    def test_a_test_source_file_is_not_judged(self):
        text = source("ui.components.songCard", "ui.navigation.CampfireDestination")
        path = "presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/songCard/SongCardTest.kt"
        self.assertEqual([], problems(path, text, PACKAGES))

    def test_a_file_outside_presentation_is_not_judged(self):
        text = source("ui.components.songCard", "ui.navigation.CampfireDestination")
        self.assertEqual([], problems("tools/screenshots/src/main/kotlin/Shot.kt", text, PACKAGES))


if __name__ == "__main__":
    unittest.main()
