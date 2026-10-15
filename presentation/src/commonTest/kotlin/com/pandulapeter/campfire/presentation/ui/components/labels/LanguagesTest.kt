/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.labels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguagesTest {

    @Test
    fun `a language is named in the app's language, capitalized`() {
        assertEquals("Hungarian", languageName(code = "hu", appLanguageCode = "en"))
        assertEquals("Magyar", languageName(code = "hu", appLanguageCode = "hu"))
    }

    @Test
    fun `a code that names no language has no name`() = assertNull(languageName(code = "qqq", appLanguageCode = "en"))

    @Test
    fun `every language named in English is named in Hungarian too`() {
        val unnamed = LANGUAGE_CODES.filter { languageName(code = it, appLanguageCode = "en") != null && languageName(code = it, appLanguageCode = "hu") == null }
        assertEquals(emptyList(), unnamed)
    }

    @Test
    fun `the picker offers every known code and the library's own, once each, in the alphabet of their labels`() {
        val languages = pickableLanguages(appLanguageCode = "en", alsoOffer = listOf("hu", "qqq"), normalize = { it.lowercase() })
        assertEquals(LANGUAGE_CODES.size + 1, languages.size)
        assertEquals(languages.size, languages.map { it.code }.distinct().size)
        assertEquals(languages.map { it.sortKey }.sorted(), languages.map { it.sortKey })
        assertTrue(languages.single { it.code == "hu" }.isInLibrary)
        assertFalse(languages.single { it.code == "de" }.isInLibrary)
    }

    @Test
    fun `a language the platform cannot name is listed only where the library holds it, under its code`() {
        val inLibrary = pickableLanguages(appLanguageCode = "en", alsoOffer = listOf("qqq"), normalize = { it.lowercase() }).single { it.code == "qqq" }
        assertNull(inLibrary.name)
        assertEquals("QQQ", inLibrary.label)
        assertTrue(inLibrary.isListed)
        assertFalse(PickableLanguage(code = "qqq", name = null, isInLibrary = false, normalize = { it }).isListed)
        assertTrue(PickableLanguage(code = "hu", name = "Hungarian", isInLibrary = false, normalize = { it }).isListed)
    }
}
