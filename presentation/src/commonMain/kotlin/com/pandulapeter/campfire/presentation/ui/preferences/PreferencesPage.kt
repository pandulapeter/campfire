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
 * every screen it affects. The order is the same in the sheet's buttons and in Settings' tabs.
 */
enum class PreferencesPage {
    GENERAL,
    SONG,
    CHORDS,
    METRONOME,
}

/**
 * Where the Preferences sheet is opened from, which decides the pages it has: only those that change something on that
 * screen, a page that would change nothing there being left out rather than disabled (Settings has a tab for every
 * one). General changes every screen, so it is always there. The Metronome tab opens no sheet, its page being on the
 * tab itself.
 */
enum class PreferencesContext(
    val pages: List<PreferencesPage>,
) {
    SONG_DETAILS(PreferencesPage.entries),
    CHORD_TOOLS(listOf(PreferencesPage.GENERAL, PreferencesPage.CHORDS)),
}

/**
 * The pages [context] shows, without the ones whose feature is switched off in Settings → Features: the Song page goes
 * with the songs, the Metronome page with the metronome. General always stays, and so does the Chords page, since the
 * Chords and the Scales tabs name chords too.
 */
internal fun preferencesPages(
    context: PreferencesContext,
    userPreferences: UserPreferences?,
) = context.pages.filter { page ->
    when (page) {
        PreferencesPage.GENERAL, PreferencesPage.CHORDS -> true
        PreferencesPage.SONG -> userPreferences?.areSongsEnabled != false
        PreferencesPage.METRONOME -> userPreferences?.isMetronomeEnabled != false
    }
}
