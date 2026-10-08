/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongTempoTest {

    private fun song(tempo: Int?) = Song(
        fileName = FILE_NAME,
        title = "Title",
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
        tempo = tempo,
    )

    private val tempos = Tempos(library = mapOf(FILE_NAME to 100), bySetlist = mapOf(SETLIST to mapOf(FILE_NAME to 90)))

    @Test
    fun `a setlist reads its own entry only`() = assertEquals(EffectiveTempo(90, Source.SETLIST, 96), effectiveTempo(song(96), SETLIST, tempos))

    @Test
    fun `the library reads the override`() = assertEquals(EffectiveTempo(100, Source.LIBRARY_OVERRIDE, 96), effectiveTempo(song(96), null, tempos))

    @Test
    fun `a setlist without an entry does not inherit the library`() =
        assertEquals(EffectiveTempo(96, Source.FILE, 96), effectiveTempo(song(96), "other.setlist.json", tempos))

    @Test
    fun `a song without a tempo plays the default`() =
        assertEquals(EffectiveTempo(120, Source.DEFAULT, 120), effectiveTempo(song(null), "other.setlist.json", tempos))

    @Test
    fun `an override equal to the song's own is no override`() {
        val tempo = effectiveTempo(song(100), null, tempos)
        assertEquals(Source.FILE, tempo.source)
        assertTrue(tempo.isDefault)
    }

    @Test
    fun `a file tempo out of range is held within it for playback`() = assertEquals(300, effectiveTempo(song(400), "other.setlist.json", tempos).bpm)

    @Test
    fun `an override is highlighted`() = assertFalse(effectiveTempo(song(96), SETLIST, tempos).isDefault)

    @Test
    fun `only a tempo something names is shown`() {
        assertEquals(90, effectiveTempo(song(96), SETLIST, tempos).displayedBpm)
        assertEquals(96, effectiveTempo(song(96), "other.setlist.json", tempos).displayedBpm)
        assertEquals(null, effectiveTempo(song(null), "other.setlist.json", tempos).displayedBpm)
    }

    @Test
    fun `overrides are set and removed by key`() {
        val changed = tempos.with(SongPlace(FILE_NAME, SETLIST), null).with(SongPlace(FILE_NAME, null), 80)
        assertEquals(null, changed[FILE_NAME, SETLIST])
        assertEquals(80, changed[FILE_NAME, null])
    }

    @Test
    fun `an override rewrites the tempo line`() {
        val song = ChordProSong(metadata = ChordProMetadata(tempo = "96 bpm"), blocks = emptyList())
        assertEquals("110", song.withTempo(110).metadata.tempo)
        assertEquals("96 bpm", song.withTempo(null).metadata.tempo)
    }

    @Test
    fun `a later tempo keeps its ratio to the opening one`() {
        assertEquals(55, sectionBpm(sectionFileBpm = 60, songFileBpm = 120, playedBpm = 110))
        assertEquals(60, sectionBpm(sectionFileBpm = 60, songFileBpm = 120, playedBpm = 120))
        assertEquals(110, sectionBpm(sectionFileBpm = null, songFileBpm = 120, playedBpm = 110))
        assertEquals(60, sectionBpm(sectionFileBpm = 60, songFileBpm = null, playedBpm = 110))
        assertEquals(300, sectionBpm(sectionFileBpm = 280, songFileBpm = 100, playedBpm = 200))
        assertEquals(30, sectionBpm(sectionFileBpm = 30, songFileBpm = 120, playedBpm = 60))
    }

    @Test
    fun `a later tempo keeps its ratio to the opening one as the click plays it`() {
        val song = ChordProSong(
            metadata = ChordProMetadata(tempo = "400"),
            blocks = listOf(ChordProBlock.Timing(tempo = "200", time = null)),
        )
        assertEquals("100", (song.withTempo(150).blocks.single() as ChordProBlock.Timing).tempo)
    }

    @Test
    fun `a change at the song's own out of range tempo follows the override`() {
        val song = ChordProSong(
            metadata = ChordProMetadata(tempo = "400"),
            blocks = listOf(ChordProBlock.Timing(tempo = "400", time = "3/4")),
        )
        assertEquals("150", (song.withTempo(150).blocks.single() as ChordProBlock.Timing).tempo)
    }

    @Test
    fun `a change out of range is held before it is scaled`() {
        val song = ChordProSong(
            metadata = ChordProMetadata(tempo = "120"),
            blocks = listOf(ChordProBlock.Timing(tempo = "400", time = null)),
        )
        assertEquals("150", (song.withTempo(60).blocks.single() as ChordProBlock.Timing).tempo)
    }

    @Test
    fun `an override scales the changes further down`() {
        val song = ChordProSong(
            metadata = ChordProMetadata(tempo = "120"),
            blocks = listOf(ChordProBlock.Timing(tempo = "60", time = "3/4"), ChordProBlock.Timing(tempo = null, time = "6/8")),
        )
        assertEquals(
            listOf(ChordProBlock.Timing(tempo = "55", time = "3/4"), ChordProBlock.Timing(tempo = "110", time = "6/8")),
            song.withTempo(110).blocks,
        )
        assertEquals(song.blocks, song.withTempo(null).blocks)
    }

    private companion object {
        const val FILE_NAME = "a.cho"
        const val SETLIST = "s.setlist.json"
    }
}
