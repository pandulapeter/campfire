/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

import com.pandulapeter.campfire.chordpro.syntax.ChordProMetaItems
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageCodesTest {

    @Test
    fun `a retired code is read as the one that replaced it`() {
        assertEquals("he", LanguageCodes.current("iw"))
        assertEquals("yi", LanguageCodes.current("ji"))
        assertEquals("id", LanguageCodes.current("in"))
        assertEquals("ro", LanguageCodes.current("mo"))
    }

    @Test
    fun `a current code is kept as it is`() {
        listOf("he", "en", "hu", "yi", "rom").forEach { assertEquals(it, LanguageCodes.current(it)) }
    }

    @Test
    fun `a song's retired language code is filed under the current one`() {
        assertEquals("he", ChordProMetaItems.languageCode("iw"))
        assertEquals("he", ChordProMetaItems.languageCode("IW-il"))
        assertEquals("he", ChordProMetaItems.languageCode("heb"))
        assertEquals("yi", ChordProMetaItems.languageCode("ji"))
    }
}
