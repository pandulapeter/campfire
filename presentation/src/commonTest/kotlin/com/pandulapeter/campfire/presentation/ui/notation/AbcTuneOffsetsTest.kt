/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals

class AbcTuneOffsetsTest {

    @Test
    fun `a tune is moved by the key change it stands after, as the chords around it are`() {
        val song = ChordProParser.parse(
            """
            {start_of_abc}
            X:1
            K:G
            GAB|
            {end_of_abc}
            {transpose: 2}
            {start_of_abc}
            X:2
            K:D
            DEF|
            {end_of_abc}
            {transpose: 0}
            {start_of_abc}
            X:1
            K:G
            GAB|
            {end_of_abc}
            """.trimIndent(),
        )
        assertEquals(mapOf("X:1\nK:G\nGAB|" to 0, "X:2\nK:D\nDEF|" to 2), abcTuneOffsets(song.blocks))
    }
}
