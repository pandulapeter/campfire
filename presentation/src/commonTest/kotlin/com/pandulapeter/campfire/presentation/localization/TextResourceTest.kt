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

import kotlin.test.Test
import kotlin.test.assertEquals

class TextResourceTest {

    @Test
    fun `each placeholder takes the text of its own number`() =
        assertEquals("Wonderwall by Oasis, 3 songs", "%2\$s by %1\$s, %3\$d songs".withPlainTexts("Oasis", "Wonderwall", "3"))

    @Test
    fun `a percent sign in the text is put in as it is`() =
        assertEquals("Saved 100% sure and 50 % off", "Saved %1\$s".withPlainTexts("100% sure and 50 % off"))

    @Test
    fun `text that looks like a placeholder is not filled in again`() =
        assertEquals("%2\$s and B", "%1\$s and %2\$s".withPlainTexts("%2\$s", "B"))

    @Test
    fun `a dollar sign in the text is not read as a group reference`() =
        assertEquals("Costs \$1 and \$0", "Costs %1\$s".withPlainTexts("\$1 and \$0"))

    @Test
    fun `a placeholder no text is given for stays as written`() =
        assertEquals("A and %2\$s", "%1\$s and %2\$s".withPlainTexts("A"))

    @Test
    fun `each text goes into the sentence in a first-strong isolate`() =
        assertEquals("Delete \u2068שלום!\u2069?", "Delete %1\$s?".withTexts("שלום!"))

    @Test
    fun `an empty text goes in without an isolate`() =
        assertEquals("From ", "From %1\$s".withTexts(""))

    @Test
    fun `joined parts are isolated one by one`() =
        assertEquals("\u2068Oasis\u2069 · \u2068אלבום\u2069", listOf("Oasis", "אלבום").joinIsolated(" · "))
}
