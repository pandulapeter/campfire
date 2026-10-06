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
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
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
    fun `the readable line is the one rewritten`() {
        val result = ChordProMetadataFields.set("{tempo: Moderato}\n{title: T}\n{tempo: 96}\n\nLa", mapOf(Field.TEMPO to "100"))

        assertEquals("{title: T}\n{tempo: 100}\n\nLa", result)
    }

    @Test
    fun `the lines the parser reads past are dropped`() {
        val result = ChordProMetadataFields.set("{title: A}\n{title: B}\nLa", mapOf(Field.TITLE to "C"))

        assertEquals("{title: C}\nLa", result)
    }

    @Test
    fun `the first line that says something is the one rewritten`() {
        assertEquals("{title: C}\nLa", ChordProMetadataFields.set("{title: }\n{title: A}\n{t: B}\nLa", mapOf(Field.TITLE to "C")))
        assertEquals("{duration: 3:00}\nLa", ChordProMetadataFields.set("{duration: long}\n{duration: 4:28}\nLa", mapOf(Field.DURATION to "3:00")))
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

    @Test
    fun `the key is set on the line the song starts in and leaves its modulations alone`() {
        val text = "{title: T}\n{key: G}\n[G]La\n{key: A}\n[A]La"

        assertEquals("{title: T}\n{key: C}\n[G]La\n{key: A}\n[A]La", ChordProMetadataFields.set(text, mapOf(Field.KEY to "C")))
        val cleared = ChordProMetadataFields.set(text, mapOf(Field.KEY to ""))
        assertEquals("{title: T}\n{key: }\n[G]La\n{key: A}\n[A]La", cleared)
        assertEquals(null, ChordProMetadataFields.valueOf(ChordProParser.summarize(cleared).metadata, Field.KEY))
        assertEquals("{title: T}\n{meta: key }\n[G]La\n{key: A}", ChordProMetadataFields.set("{title: T}\n{meta: key G}\n[G]La\n{key: A}", mapOf(Field.KEY to null)))
    }

    @Test
    fun `the header lines of a field the song changes mid-song are one value, and its body lines are its changes`() {
        assertEquals("{tempo: 120}\nLa", ChordProMetadataFields.set("{tempo: 90}\n{tempo: 100}\nLa", mapOf(Field.TEMPO to "120")))
        assertEquals("{title: T}\nLa", ChordProMetadataFields.set("{title: T}\n{key: G}\nLa", mapOf(Field.KEY to null)))
        assertEquals("[C]la\n{key: A}", ChordProMetadataFields.set("[C]la\n{key: G}", mapOf(Field.KEY to "A")))
        assertEquals("[C]la\n{key: A}", ChordProMetadataFields.set("[C]la\n{key: G}\n{key: A}", mapOf(Field.KEY to null)))
    }

    @Test
    fun `the tempo and the time signature are set on the lines the song starts in and leave their changes alone`() {
        val text = "{title: T}\n{time: 4/4}\n{tempo: 90}\n[C]La\n{time: 3/4}\n{tempo: 140}\n[G]La"

        assertEquals(
            "{title: T}\n{time: 6/8}\n{tempo: 90}\n[C]La\n{time: 3/4}\n{tempo: 140}\n[G]La",
            ChordProMetadataFields.set(text, mapOf(Field.TIME to "6/8")),
        )
        assertEquals(
            "{title: T}\n{time: 4/4}\n{tempo: 100}\n[C]La\n{time: 3/4}\n{tempo: 140}\n[G]La",
            ChordProMetadataFields.set(text, mapOf(Field.TEMPO to "100")),
        )
    }

    @Test
    fun `the song defaults change the opening values and every change in the body stays one`() {
        val text = "{title: T}\n{tempo: 120}\n{time: 4/4}\n\n[C]La\n{tempo: 90}\n{time: 3/4}\n[G]La"
        val changes = listOf(ChordProBlock.Timing(tempo = "90", time = "3/4"))

        val edited = ChordProMetadataFields.set(text, mapOf(Field.TEMPO to "100", Field.TIME to "6/8"))
        assertEquals("100", ChordProParser.parse(edited).metadata.tempo)
        assertEquals(changes, ChordProParser.parse(edited).blocks.filterIsInstance<ChordProBlock.Timing>())

        val cleared = ChordProMetadataFields.set(text, mapOf(Field.TEMPO to null))
        assertEquals("{title: T}\n{tempo: }\n{time: 4/4}\n\n[C]La\n{tempo: 90}\n{time: 3/4}\n[G]La", cleared)
        assertEquals("", ChordProParser.parse(cleared).metadata.tempo)
        assertEquals(changes, ChordProParser.parse(cleared).blocks.filterIsInstance<ChordProBlock.Timing>())
    }

    @Test
    fun `the empty lines of the new song template are filled in where they stand`() {
        val template = "{title: T}\n{key: }\n{capo: }\n{tempo: }\n{time: }\n\nLa"

        assertEquals(
            "{title: T}\n{key: Am}\n{capo: 2}\n{tempo: 96}\n{time: 3/4}\n\nLa",
            ChordProMetadataFields.set(template, mapOf(Field.KEY to "Am", Field.CAPO to "2", Field.TEMPO to "96", Field.TIME to "3/4")),
        )
    }

    @Test
    fun `an empty line says nothing about how the song is played`() {
        val metadata = ChordProParser.parseMetadata("{key: }\n{capo: }\n{tempo: }\n{time: }")

        listOf(Field.KEY, Field.CAPO, Field.TEMPO, Field.TIME).forEach { assertEquals(null, ChordProMetadataFields.valueOf(metadata, it)) }
        assertEquals("96", ChordProMetadataFields.valueOf(ChordProParser.parseMetadata("{tempo: 96}\n{tempo: }"), Field.TEMPO))
    }
}
