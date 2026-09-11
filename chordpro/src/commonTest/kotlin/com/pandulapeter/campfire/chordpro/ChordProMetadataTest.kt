/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.displayTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProMetadataTest {

    @Test
    fun `the subtitle follows the title in parentheses`() {
        val metadata = ChordProMetadata(title = "Wagon Wheel", subtitle = "Live")

        assertEquals("Wagon Wheel (Live)", metadata.displayTitle(fallback = "song"))
    }

    @Test
    fun `a song with no subtitle is named by its title alone`() {
        val metadata = ChordProMetadata(title = "Wagon Wheel")

        assertEquals("Wagon Wheel", metadata.displayTitle(fallback = "song"))
    }

    @Test
    fun `the fallback stands in for a title the file does not declare`() {
        val metadata = ChordProMetadata(subtitle = "Live")

        assertEquals("song (Live)", metadata.displayTitle(fallback = "song"))
    }

    @Test
    fun `a subtitle that repeats the title is not appended to it`() {
        val metadata = ChordProMetadata(title = "Wagon Wheel", subtitle = "wagon wheel")

        assertEquals("Wagon Wheel", metadata.displayTitle(fallback = "song"))
    }

    @Test
    fun `blank directives count as absent`() {
        val metadata = ChordProMetadata(title = "  ", subtitle = "  ")

        assertEquals("song", metadata.displayTitle(fallback = "song"))
    }
}
