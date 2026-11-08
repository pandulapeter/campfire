/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * One page of the Preferences, grouped by what it is about rather than by the screen it changes, so that one page serves
 * every screen it affects. The order is the tab row's everywhere.
 */
enum class PreferencesPage {
    SONG,
    CHORDS,
    METRONOME,
}

/**
 * Where the Preferences are shown, which decides the pages they have: Settings has every one, and the sheet only those
 * that change something on the screen it is opened from, a page that would change nothing there being left out rather
 * than disabled. The Metronome tab opens no sheet, its page being on the tab itself.
 */
enum class PreferencesContext(
    val pages: List<PreferencesPage>,
) {
    SETTINGS(PreferencesPage.entries),
    SONG_DETAILS(PreferencesPage.entries),
    CHORD_TOOLS(listOf(PreferencesPage.CHORDS)),
}

/**
 * The pages [context] shows, without the ones whose feature is switched off in Settings → Features: the Song page goes
 * with the songs, the Metronome page with the metronome. The Chords page always stays, since the Chords and the Scales
 * tabs name chords too. The first one is the page the sheet opens on.
 */
internal fun preferencesPages(
    context: PreferencesContext,
    userPreferences: UserPreferences?,
) = context.pages.filter { page ->
    when (page) {
        PreferencesPage.SONG -> userPreferences?.areSongsEnabled != false
        PreferencesPage.CHORDS -> true
        PreferencesPage.METRONOME -> userPreferences?.isMetronomeEnabled != false
    }
}
