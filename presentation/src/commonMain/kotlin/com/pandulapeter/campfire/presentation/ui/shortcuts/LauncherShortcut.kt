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

/**
 * A shortcut the system offers on a long press of the app icon: Android's launcher shortcuts, iOS's Home Screen quick
 * actions. Each opens its [link], an app link ([AppLinks]), and the shells name each by [name] towards the system, which
 * is how a shortcut pinned on Android is found again.
 */
enum class LauncherShortcut {
    NEW_SONG,
    PASTE_SONG,
    SETLISTS,
    METRONOME,
    TUNER,
    CHORDS,
    SCALES,
    ;

    /** The app link this opens. */
    val link get() = AppLinks.of(screen?.let(BrowserRoutes::pathOf) ?: action?.path.orEmpty())

    internal val screen: CampfireDestination.TopLevel?
        get() = when (this) {
            NEW_SONG, PASTE_SONG -> null
            SETLISTS -> CampfireDestination.Setlists
            METRONOME -> CampfireDestination.Metronome
            TUNER -> CampfireDestination.Tuner
            CHORDS -> CampfireDestination.Chords
            SCALES -> CampfireDestination.Scales
        }

    private val action
        get() = when (this) {
            NEW_SONG -> BrowserRoutes.LinkAction.NEW_SONG
            PASTE_SONG -> BrowserRoutes.LinkAction.PASTE_SONG
            else -> null
        }

    companion object {

        /**
         * The shortcuts offered, in the order the systems show them, which is the order they are cut short in (iOS shows
         * four, and most launchers too): New song and Paste a song where their buttons are offered (the songs on, read
         * only mode off), then the main screens switched on, in the navigation's order. Neither the home screen, which the
         * icon itself opens, nor Settings is one.
         */
        internal fun offered(features: FeatureSwitches, isReadOnly: Boolean): List<LauncherShortcut> {
            val home = CampfireDestination.TopLevel.home(features)
            val screens = CampfireDestination.TopLevel.entries(features).filter { it != home }
            return listOf(NEW_SONG, PASTE_SONG).takeIf { features.areSongsEnabled && !isReadOnly }.orEmpty() +
                LauncherShortcut.entries.filter { it.screen != null && it.screen in screens }
        }
    }
}
