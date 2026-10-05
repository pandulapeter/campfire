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

import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ChordProMetadataFieldsTest {

    @Test
    fun `a value is rewritten where it stands, in the spelling it was written in`() {
        val text = "{t: Old}\n{meta: artist Someone}\n\n[G]La"

        val result = ChordProMetadataFields.set(text, mapOf(Field.TITLE to "New", Field.ARTIST to "Somebody"))

        assertEquals("{t: New}\n{meta: artist Somebody}\n\n[G]La", result)
    }

    @Test
    fun `a missing field is written into the header in order`() {
        val text = "{title: T}\n{key: G}\n\nLa"

        val result = ChordProMetadataFields.set(text, mapOf(Field.ALBUM to "Record", Field.YEAR to "1969"))

        assertEquals("{title: T}\n{album: Record}\n{year: 1969}\n{key: G}\n\nLa", result)
    }

    @Test
    fun `a blank value removes the field`() {
        assertEquals("{title: T}\nLa", ChordProMetadataFields.set("{title: T}\n{year: 1969}\nLa", mapOf(Field.YEAR to "  ")))
        assertEquals("{title: T}\nLa", ChordProMetadataFields.set("{title: T}\n{year: 1969}\nLa", mapOf(Field.YEAR to null)))
    }

    @Test
    fun `the lines the parser reads past are dropped`() {
        val result = ChordProMetadataFields.set("{title: A}\n{title: B}\nLa", mapOf(Field.TITLE to "C"))

        assertEquals("{title: C}\nLa", result)
    }

    @Test
    fun `an unchanged text is returned as it is`() {
        val text = "{title:   T  }\r\n{artist: A}\r\n"

        assertSame(text, ChordProMetadataFields.set(text, mapOf(Field.TITLE to "T", Field.ARTIST to " A", Field.ALBUM to "")))
    }

    @Test
    fun `line endings survive an edit`() {
        assertEquals("{title: B}\r\nLa\r\n", ChordProMetadataFields.set("{title: A}\r\nLa\r\n", mapOf(Field.TITLE to "B")))
    }

    @Test
    fun `a line break in a value does not end the directive`() {
        assertEquals("{title: A B}\nLa", ChordProMetadataFields.set("{title: T}\nLa", mapOf(Field.TITLE to "A\nB")))
    }

    @Test
    fun `values are read back the way the parser reads them`() {
        val metadata = ChordProParser.parseMetadata("{title: T}\n{meta: album Record}\n{year: }")

        assertEquals("T", ChordProMetadataFields.valueOf(metadata, Field.TITLE))
        assertEquals("Record", ChordProMetadataFields.valueOf(metadata, Field.ALBUM))
        assertEquals(null, ChordProMetadataFields.valueOf(metadata, Field.YEAR))
        assertEquals(null, ChordProMetadataFields.valueOf(metadata, Field.ARTIST))
    }

    @Test
    fun `the time signature is written where the song is played rather than among what it is`() {
        assertEquals("{title: T}\n{time: 6/8}\nLa", ChordProMetadataFields.set("{title: T}\n{time: 4/4}\nLa", mapOf(Field.TIME to "6/8")))
        assertEquals("{title: T}\n{key: G}\n{time: 3/4}\nLa", ChordProMetadataFields.set("{title: T}\n{key: G}\nLa", mapOf(Field.TIME to "3/4")))
        assertEquals("3/4", ChordProMetadataFields.valueOf(ChordProParser.parseMetadata("{time: 3/4}"), Field.TIME))
    }
}
