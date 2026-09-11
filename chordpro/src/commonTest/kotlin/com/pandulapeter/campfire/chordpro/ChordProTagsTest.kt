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

import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProTagsTest {

    @Test
    fun `tags are read from the tag directive and from its meta spelling`() {
        val metadata = ChordProParser.parseMetadata("{title: T}\n{tag: campfire}\n{meta: tag Needs study}")

        assertEquals(listOf("campfire", "Needs study"), metadata.tags)
    }

    @Test
    fun `the value of a tag directive is taken whole`() {
        val metadata = ChordProParser.parseMetadata("{tag: slow, but not too slow}")

        assertEquals(listOf("slow, but not too slow"), metadata.tags)
    }

    @Test
    fun `a tag repeated in another spelling is read once`() {
        val metadata = ChordProParser.parseMetadata("{tag: Campfire}\n{tag: campfire}")

        assertEquals(listOf("Campfire"), metadata.tags)
    }

    @Test
    fun `tags do not end up among the custom metadata`() {
        val metadata = ChordProParser.parseMetadata("{tag: campfire}\n{meta: tuning DADGAD}")

        assertEquals(mapOf("tuning" to listOf("DADGAD")), metadata.custom)
    }

    @Test
    fun `serializing writes every tag as its own directive`() {
        val song = ChordProParser.parse("{title: T}\n{meta: tag slow}\n{tag: campfire}")

        assertEquals("{title: T}\n{tag: slow}\n{tag: campfire}", ChordProSerializer.serialize(song))
    }

    @Test
    fun `a new tag is written after the last one the song already has`() {
        val text = "{title: T}\n{tag: slow}\n{artist: A}\n\nThe first line"

        assertEquals("{title: T}\n{tag: slow}\n{tag: campfire}\n{artist: A}\n\nThe first line", ChordProTags.addTag(text, "campfire"))
    }

    @Test
    fun `the first tag of a song is written after its metadata`() {
        val text = "{title: T}\n{artist: A}\n\n{start_of_verse}\nThe first line\n{end_of_verse}"

        assertEquals(
            "{title: T}\n{artist: A}\n{tag: campfire}\n\n{start_of_verse}\nThe first line\n{end_of_verse}",
            ChordProTags.addTag(text, "campfire"),
        )
    }

    @Test
    fun `a song that opens with its lyrics gets the tag above them`() {
        assertEquals("{tag: campfire}\nThe first line", ChordProTags.addTag("The first line", "campfire"))
    }

    @Test
    fun `a comment heading is not metadata to write a tag after`() {
        val text = "{title: T}\n{comment: Intro}\nThe first line"

        assertEquals("{title: T}\n{tag: campfire}\n{comment: Intro}\nThe first line", ChordProTags.addTag(text, "campfire"))
    }

    @Test
    fun `a tag the song already carries is not written twice`() {
        val text = "{tag: Campfire}\nThe first line"

        assertEquals(text, ChordProTags.addTag(text, "  campfire  "))
    }

    @Test
    fun `removing a tag takes its whole line with it`() {
        val text = "{title: T}\n{tag: slow}\n{tag: campfire}\nThe first line"

        assertEquals("{title: T}\n{tag: campfire}\nThe first line", ChordProTags.removeTag(text, "SLOW"))
    }

    @Test
    fun `removing a tag finds it in either spelling`() {
        val text = "{meta: tag slow}\n{tag: campfire}\nThe first line"

        assertEquals("{tag: campfire}\nThe first line", ChordProTags.removeTag(text, "slow"))
    }

    @Test
    fun `the lyrics are left alone by both edits`() {
        val text = "{title: T}\n\n[Am]The first [C]line\n  indented, with {braces} in it"

        assertEquals(text, ChordProTags.removeTag(ChordProTags.addTag(text, "campfire"), "campfire"))
    }
}
