/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutDirectionsTest {

    @Test
    fun `every string table belongs to the app language of its code`() {
        AppLocale.entries.forEach { locale -> assertEquals(locale.code, locale.appLanguage.id) }
    }

    @Test
    fun `every language the app speaks today is laid out from the left`() {
        AppLocale.entries.forEach { locale -> assertEquals(LayoutDirection.Ltr, locale.layoutDirection, locale.code) }
    }

    @Test
    fun `a language chosen in the app wins over the system's`() {
        assertEquals(AppLocale.HU, resolvedAppLocale(language = AppLanguage.HUNGARIAN, systemLanguage = "de"))
    }

    @Test
    fun `the system's language is found by its current code when the user left it to the system`() {
        assertEquals(AppLocale.DE, resolvedAppLocale(language = AppLanguage.SYSTEM_DEFAULT, systemLanguage = "de"))
        assertEquals(AppLocale.RO, resolvedAppLocale(language = null, systemLanguage = "mo"))
        assertEquals(AppLocale.DEFAULT, resolvedAppLocale(language = AppLanguage.SYSTEM_DEFAULT, systemLanguage = "ja"))
    }
}
