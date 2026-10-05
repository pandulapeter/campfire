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

        assertEquals(listOf(RenderSection.Metadata(metadata)) + body, withMetadataSection(body, metadata, shouldShowChords = true, isSongInfoShown = true))
    }

    @Test
    fun `playing metadata is the metadata section on its own`() {
        val metadata = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8")

        assertEquals(listOf(RenderSection.Metadata(metadata), verse), withMetadataSection(listOf(verse), metadata, shouldShowChords = true, isSongInfoShown = true))
    }

    @Test
    fun `a cover, a capo of zero and blank values create no section`() {
        val body = listOf(verse)

        assertSame(body, withMetadataSection(body, ChordProMetadata(coverArt = "https://example.com/cover.jpg", capo = 0), shouldShowChords = true, isSongInfoShown = true))
        assertSame(body, withMetadataSection(body, ChordProMetadata(album = " ", composer = "", key = " "), shouldShowChords = true, isSongInfoShown = true))
    }

    @Test
    fun `each kind of chip can be the metadata section on its own`() {
        listOf(
            ChordProMetadata(tags = listOf("Folk")),
            ChordProMetadata(languages = listOf("en", "hu")),
            ChordProMetadata(links = listOf(ChordProLink("https://example.com"))),
        ).forEach { metadata ->
            assertEquals(listOf(RenderSection.Metadata(metadata)), withMetadataSection(emptyList(), metadata, shouldShowChords = true, isSongInfoShown = true))
        }
    }

    @Test
    fun `what the song is creates no section where its card is not shown`() {
        val body = listOf(verse)
        val info = ChordProMetadata(album = "Album", tags = listOf("Folk"), links = listOf(ChordProLink("https://example.com")))

        assertSame(body, withMetadataSection(body, info, shouldShowChords = true, isSongInfoShown = false))
        assertEquals(
            listOf(RenderSection.Metadata(info.copy(key = "G")), verse),
            withMetadataSection(body, info.copy(key = "G"), shouldShowChords = true, isSongInfoShown = false),
        )
    }

    @Test
    fun `an editable card is there for a song that says nothing about itself yet`() {
        val body = listOf(verse)

        assertEquals(
            listOf(RenderSection.Metadata(ChordProMetadata()), verse),
            withMetadataSection(body, ChordProMetadata(), shouldShowChords = true, isSongInfoShown = true, isSongInfoEditable = true),
        )
        assertSame(body, withMetadataSection(body, ChordProMetadata(), shouldShowChords = true, isSongInfoShown = false, isSongInfoEditable = true))
    }

    @Test
    fun `without chords and metronome the playing metadata is left out`() {
        val playing = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8")
        val body = listOf(verse)

        assertSame(body, withMetadataSection(body, playing, shouldShowChords = false, shouldShowTempo = false, isSongInfoShown = true))
        assertEquals(
            listOf(RenderSection.Metadata(ChordProMetadata(album = "Album")), verse),
            withMetadataSection(body, playing.copy(album = "Album"), shouldShowChords = false, shouldShowTempo = false, isSongInfoShown = true),
        )
    }

    @Test
    fun `the chords and the metronome each take their own two values away`() {
        val playing = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8")
        val body = listOf(verse)

        assertEquals(
            listOf(RenderSection.Metadata(ChordProMetadata(tempo = "96", time = "6/8")), verse),
            withMetadataSection(body, playing, shouldShowChords = false, isSongInfoShown = true),
        )
        assertEquals(
            listOf(RenderSection.Metadata(ChordProMetadata(key = "G", capo = 2)), verse),
            withMetadataSection(body, playing, shouldShowChords = true, shouldShowTempo = false, isSongInfoShown = true),
        )
        assertEquals(
            listOf(RenderSection.Metadata(metadata = ChordProMetadata(time = "4/4"), readsCapoAndTime = true), verse),
            withMetadataSection(body, ChordProMetadata(), shouldShowChords = false, isSongInfoShown = false, readsCapoAndTime = true),
        )
    }

    @Test
    fun `the playing controls are a section of their own for a song that names none of the four`() {
        val body = listOf(verse)

        assertEquals(
            listOf(RenderSection.Metadata(metadata = ChordProMetadata(), hasPlayingControls = true), verse),
            withMetadataSection(body, ChordProMetadata(), shouldShowChords = true, isSongInfoShown = false, hasPlayingControls = true),
        )
    }

    @Test
    fun `without chords and metronome the playing controls are left out with the values`() {
        val body = listOf(verse)
        val playing = ChordProMetadata(key = "G", capo = 2, tempo = "96", time = "6/8")

        assertSame(body, withMetadataSection(body, playing, shouldShowChords = false, shouldShowTempo = false, isSongInfoShown = false, hasPlayingControls = true))
    }

    @Test
    fun `read only mode names a capo of none and the common time where the file names neither`() {
        val body = listOf(verse)

        assertEquals(
            listOf(RenderSection.Metadata(metadata = ChordProMetadata(capo = 0, time = "4/4"), readsCapoAndTime = true), verse),
            withMetadataSection(body, ChordProMetadata(), shouldShowChords = true, isSongInfoShown = false, readsCapoAndTime = true),
        )
        assertEquals(
            listOf(RenderSection.Metadata(metadata = ChordProMetadata(capo = 3, time = "6/8"), readsCapoAndTime = true), verse),
            withMetadataSection(body, ChordProMetadata(capo = 3, time = "6/8"), shouldShowChords = true, isSongInfoShown = false, readsCapoAndTime = true),
        )
        assertSame(body, withMetadataSection(body, ChordProMetadata(), shouldShowChords = false, shouldShowTempo = false, isSongInfoShown = false, readsCapoAndTime = true))
    }
}
