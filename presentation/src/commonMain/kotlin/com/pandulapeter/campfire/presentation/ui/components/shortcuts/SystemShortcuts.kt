/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.platform.AppBuild
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.presentation.ui.platform.isAppleKeyboard

/** One chord of a [SystemShortcutGroup], named by its row's [description]. */
@Immutable
internal data class SystemShortcut(val description: String, val chord: KeyChord)

/** One group of the shortcuts a platform lists itself: Android's Meta + / list, iOS's Command-hold overlay. */
@Immutable
internal data class SystemShortcutGroup(val title: String, val shortcuts: List<SystemShortcut>)

/**
 * The shortcuts the sheet lists ([shortcutGroups]) as a platform's own list takes them, one chord at a time, each named
 * by its row: the system writes the keys itself, so a pair's halves are two entries of one description. A number,
 * which needs the name of the screen it opens, is left out (none works on a phone or a tablet), and so is the
 * scroll wheel, which is no key.
 */
internal fun systemShortcutGroups(build: AppBuild, features: ShortcutFeatures, isApple: Boolean): List<Pair<ShortcutScope, List<KeyShortcut>>> =
    shortcutGroups(build = build, features = features, isApple = isApple, isPreviewWheelZoomed = false).mapNotNull { group ->
        val shortcuts = group.rows.filter { it.lead !in KeyShortcut.screens }.flatMap { it.shortcuts }
        if (shortcuts.isEmpty()) null else group.scope to shortcuts
    }

/** [systemShortcutGroups] for this platform, in the app's language, which a shell hands to its system as it changes. */
@Composable
internal fun systemShortcutTexts(features: ShortcutFeatures): List<SystemShortcutGroup> {
    val groups = remember(features) { systemShortcutGroups(build = appBuild, features = features, isApple = isAppleKeyboard) }
    return groups.map { (scope, shortcuts) ->
        SystemShortcutGroup(
            title = stringResource(scope.title),
            shortcuts = shortcuts.map { shortcut ->
                SystemShortcut(description = stringResource(requireNotNull(shortcut.rowLead.descriptionIn(scope))), chord = shortcut.chord)
            },
        )
    }
}
