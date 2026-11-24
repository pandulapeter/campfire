/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.shortcuts

import com.pandulapeter.campfire.presentation.ui.navigation.AppLinks
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserRoutes
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LauncherShortcutTest {

    private val everything = FeatureSwitches(
        areSongsEnabled = true,
        areSetlistsEnabled = true,
        isMetronomeEnabled = true,
        isTunerEnabled = true,
        isChordLibraryEnabled = true,
        areScalesEnabled = true,
        areScaleSuggestionsEnabled = true,
    )

    @Test
    fun `everything switched on offers paste then metronome then tuner then setlists then new song`() = assertEquals(
        listOf(
            LauncherShortcut.PASTE_SONG,
            LauncherShortcut.METRONOME,
            LauncherShortcut.TUNER,
            LauncherShortcut.SETLISTS,
            LauncherShortcut.NEW_SONG,
        ),
        LauncherShortcut.offered(features = everything, isReadOnly = false),
    )

    @Test
    fun `read only mode takes the two actions and leaves the screens`() = assertEquals(
        listOf(LauncherShortcut.METRONOME, LauncherShortcut.TUNER, LauncherShortcut.SETLISTS),
        LauncherShortcut.offered(features = everything, isReadOnly = true),
    )

    @Test
    fun `a screen switched off is no shortcut`() = assertEquals(
        listOf(LauncherShortcut.PASTE_SONG, LauncherShortcut.SETLISTS, LauncherShortcut.NEW_SONG),
        LauncherShortcut.offered(features = everything.copy(isMetronomeEnabled = false, isTunerEnabled = false), isReadOnly = false),
    )

    @Test
    fun `with the songs off the first tool is the home screen and nothing of the songs is offered`() = assertEquals(
        listOf(LauncherShortcut.TUNER),
        LauncherShortcut.offered(features = everything.copy(areSongsEnabled = false, areSetlistsEnabled = false), isReadOnly = false),
    )

    @Test
    fun `with every feature off nothing is offered`() = assertEquals(
        emptyList(),
        LauncherShortcut.offered(
            features = FeatureSwitches(
                areSongsEnabled = false,
                areSetlistsEnabled = false,
                isMetronomeEnabled = false,
                isTunerEnabled = false,
                isChordLibraryEnabled = false,
                areScalesEnabled = false,
                areScaleSuggestionsEnabled = false,
            ),
            isReadOnly = false,
        ),
    )

    @Test
    fun `every combination of switches keeps the order and offers neither a screen switched off nor the home screen`() {
        (0 until 64).forEach { bits ->
            val features = FeatureSwitches(
                areSongsEnabled = bits and 1 != 0,
                areSetlistsEnabled = bits and 1 != 0 && bits and 2 != 0,
                isMetronomeEnabled = bits and 4 != 0,
                isTunerEnabled = bits and 8 != 0,
                isChordLibraryEnabled = bits and 16 != 0,
                areScalesEnabled = bits and 32 != 0,
                areScaleSuggestionsEnabled = false,
            )
            listOf(false, true).forEach { isReadOnly ->
                val offered = LauncherShortcut.offered(features = features, isReadOnly = isReadOnly)
                assertEquals(offered.sortedBy { it.ordinal }, offered)
                val enabled = CampfireDestination.TopLevel.entries(features)
                assertTrue(offered.mapNotNull { it.screen }.all { it != enabled.first() && it in enabled })
                assertEquals(features.areSongsEnabled && !isReadOnly, LauncherShortcut.PASTE_SONG in offered)
            }
        }
    }

    @Test
    fun `each shortcut's link opens its screen or names its action`() {
        assertEquals(BrowserRoutes.LinkAction.NEW_SONG, AppLinks.pathOf(LauncherShortcut.NEW_SONG.link)?.let(BrowserRoutes.LinkAction::of))
        assertEquals(BrowserRoutes.LinkAction.PASTE_SONG, AppLinks.pathOf(LauncherShortcut.PASTE_SONG.link)?.let(BrowserRoutes.LinkAction::of))
        LauncherShortcut.entries.mapNotNull { it.screen }.forEach { screen ->
            assertEquals(BrowserRoutes.pathOf(screen), AppLinks.pathOf(LauncherShortcut.entries.first { it.screen == screen }.link))
        }
    }
}
