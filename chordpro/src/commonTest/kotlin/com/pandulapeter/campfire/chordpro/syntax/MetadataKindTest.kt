/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataKindTest {

    @Test
    fun `a directive lands in the header between the kinds that come before and after it`() {
        val header = "{title: T}\n{subtitle: S}\n{artist: A}\n{album: L}\n{key: G}\n{tempo: 90}\n{tag: slow}\n\nla"

        assertEquals("{title: T}\n{subtitle: S}\n{artist: A}\n{album: L}\n{year: }\n{key: G}", header.insert("year").substringBefore("\n{tempo"))
        assertEquals("{tempo: 90}\n{duration: }\n{tag: slow}", header.insert("duration").substringAfter("{key: G}\n").substringBefore("\n\n"))
        assertEquals("{tag: slow}\n{link: }\n\nla", header.insert("link").substringAfter("{tempo: 90}\n"))
    }

    @Test
    fun `the short spellings are read as the directives they stand for`() {
        val metadata = ChordProParser.parseMetadata("{t: X}\n{st: Y}\n{lang: hu}")

        assertEquals("X", metadata.title)
        assertEquals("Y", metadata.subtitle)
        assertEquals(listOf("hu"), metadata.languages)
    }

    @Test
    fun `only the tempo and the time signature change from where they stand in the body`() {
        val song = ChordProParser.parse("{key: G}\n{capo: 2}\n{tempo: 100}\n{time: 4/4}\n\nla\n{key: A}\n{capo: 3}\n{tempo: 90}\n{time: 3/4}\nlo")

        assertEquals("G", song.metadata.key)
        assertEquals(2, song.metadata.capo)
        assertEquals(listOf("90" to "3/4"), song.blocks.filterIsInstance<ChordProBlock.Timing>().map { it.tempo to it.time })
    }

    private fun String.insert(name: String): String {
        val insertion = ChordProHeader.insert(this, name = name, prefix = "{$name: ", suffix = "}")
        return replaceRange(insertion.offset, insertion.offset, insertion.text)
    }
}
