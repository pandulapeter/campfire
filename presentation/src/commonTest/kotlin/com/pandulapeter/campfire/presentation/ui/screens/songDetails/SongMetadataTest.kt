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

        assertEquals(listOf(RenderSection.Metadata(metadata)) + body, withMetadataSection(body, metadata))
    }

    @Test
    fun `playing metadata and a cover alone do not create an empty card`() {
        val body = listOf(verse)
        val metadata = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8", coverArt = "https://example.com/cover.jpg")

        assertSame(body, withMetadataSection(body, metadata))
        assertSame(body, withMetadataSection(body, ChordProMetadata(album = " ", composer = "")))
    }

    @Test
    fun `each kind of chip can be the metadata section on its own`() {
        listOf(
            ChordProMetadata(tags = listOf("Folk")),
            ChordProMetadata(languages = listOf("en", "hu")),
            ChordProMetadata(links = listOf(ChordProLink("https://example.com"))),
        ).forEach { metadata ->
            assertEquals(listOf(RenderSection.Metadata(metadata)), withMetadataSection(emptyList(), metadata))
        }
    }
}
