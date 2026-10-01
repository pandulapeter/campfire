/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SongMetadataTest {

    private val verse = RenderSection.Comment(text = "Verse", style = CommentStyle.PLAIN)

    @Test
    fun `descriptive metadata is the first section without changing the body order`() {
        val metadata = ChordProMetadata(album = "Album", tags = listOf("Folk"), languages = listOf("en", "hu"))
        val body = listOf(verse, RenderSection.Comment(text = "Chorus", style = CommentStyle.PLAIN))

        assertEquals(listOf(RenderSection.Metadata(metadata)) + body, withMetadataSection(body, metadata, shouldShowChords = true))
    }

    @Test
    fun `playing metadata is the metadata section on its own`() {
        val metadata = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8")

        assertEquals(listOf(RenderSection.Metadata(metadata), verse), withMetadataSection(listOf(verse), metadata, shouldShowChords = true))
    }

    @Test
    fun `a cover, a capo of zero and blank values create no section`() {
        val body = listOf(verse)

        assertSame(body, withMetadataSection(body, ChordProMetadata(coverArt = "https://example.com/cover.jpg", capo = 0), shouldShowChords = true))
        assertSame(body, withMetadataSection(body, ChordProMetadata(album = " ", composer = "", key = " "), shouldShowChords = true))
    }

    @Test
    fun `each kind of chip can be the metadata section on its own`() {
        listOf(
            ChordProMetadata(tags = listOf("Folk")),
            ChordProMetadata(languages = listOf("en", "hu")),
            ChordProMetadata(links = listOf(ChordProLink("https://example.com"))),
        ).forEach { metadata ->
            assertEquals(listOf(RenderSection.Metadata(metadata)), withMetadataSection(emptyList(), metadata, shouldShowChords = true))
        }
    }

    @Test
    fun `lyrics-only mode leaves out the playing metadata`() {
        val playing = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8")
        val body = listOf(verse)

        assertSame(body, withMetadataSection(body, playing, shouldShowChords = false))
        assertEquals(
            listOf(RenderSection.Metadata(ChordProMetadata(album = "Album")), verse),
            withMetadataSection(body, playing.copy(album = "Album"), shouldShowChords = false),
        )
    }
}
