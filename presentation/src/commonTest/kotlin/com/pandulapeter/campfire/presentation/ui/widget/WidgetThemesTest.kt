/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import androidx.compose.ui.graphics.toArgb
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WidgetThemesTest {

    @Test
    fun `the widgets take the app's light or dark half and follow the system where the app does`() {
        assertEquals(false, widgetTheme(uiMode = UserPreferences.UiMode.LIGHT, colors = null).isDark)
        assertEquals(true, widgetTheme(uiMode = UserPreferences.UiMode.DARK, colors = null).isDark)
        assertNull(widgetTheme(uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT, colors = null).isDark)
    }

    @Test
    fun `the widgets are painted in both halves of the app's palette`() {
        val theme = widgetTheme(uiMode = null, colors = CampfireColorScheme)
        assertEquals(CampfireColorScheme.light.primary.toArgb(), theme.light?.accent?.toInt())
        assertEquals(CampfireColorScheme.dark.onSurface.toArgb(), theme.dark?.text?.toInt())
    }

    @Test
    fun `the system's own colors leave the widgets to the system`() {
        val theme = widgetTheme(uiMode = UserPreferences.UiMode.DARK, colors = null)
        assertNull(theme.light)
        assertNull(theme.dark)
    }

    @Test
    fun `a snapshot written before the theme was part of one is read without it`() {
        val snapshot = WidgetSnapshot(
            songs = emptyList(),
            setlists = emptyList(),
            areSongsEnabled = true,
            areSetlistsEnabled = true,
            homeLink = "campfire://",
            setlistsLink = "campfire://setlists",
            strings = WidgetTestStrings.strings,
        )
        val withoutTheme = snapshot.encode()
        assertFalse("theme" in withoutTheme)
        assertEquals(snapshot, WidgetSnapshot.decode(withoutTheme))
        val themed = snapshot.copy(theme = widgetTheme(uiMode = UserPreferences.UiMode.DARK, colors = CampfireColorScheme))
        assertEquals(themed, WidgetSnapshot.decode(themed.encode()))
    }
}
