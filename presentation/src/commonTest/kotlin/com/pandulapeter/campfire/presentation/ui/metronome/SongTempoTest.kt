/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.metronome.EffectiveTempo.Source
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
    fun aSetlistReadsItsOwnEntryOnly() = assertEquals(EffectiveTempo(90, Source.SETLIST, 96), effectiveTempo(song(96), SETLIST, tempos))

    @Test
    fun theLibraryReadsTheOverride() = assertEquals(EffectiveTempo(100, Source.LIBRARY_OVERRIDE, 96), effectiveTempo(song(96), null, tempos))

    @Test
    fun aSetlistWithoutAnEntryDoesNotInheritTheLibrary() =
        assertEquals(EffectiveTempo(96, Source.FILE, 96), effectiveTempo(song(96), "other.setlist.json", tempos))

    @Test
    fun aSongWithoutATempoPlaysTheDefault() =
        assertEquals(EffectiveTempo(120, Source.DEFAULT, 120), effectiveTempo(song(null), "other.setlist.json", tempos))

    @Test
    fun anOverrideEqualToTheSongsOwnIsNoOverride() {
        val tempo = effectiveTempo(song(100), null, tempos)
        assertEquals(Source.FILE, tempo.source)
        assertTrue(tempo.isDefault)
    }

    @Test
    fun aFileTempoOutOfRangeIsHeldWithinItForPlayback() = assertEquals(300, effectiveTempo(song(400), "other.setlist.json", tempos).bpm)

    @Test
    fun anOverrideIsHighlighted() = assertFalse(effectiveTempo(song(96), SETLIST, tempos).isDefault)

    @Test
    fun overridesAreSetAndRemovedByKey() {
        val changed = tempos.with(TempoKey(FILE_NAME, SETLIST), null).with(TempoKey(FILE_NAME, null), 80)
        assertEquals(null, changed[FILE_NAME, SETLIST])
        assertEquals(80, changed[FILE_NAME, null])
    }

    @Test
    fun anOverrideRewritesTheTempoLine() {
        val song = ChordProSong(metadata = ChordProMetadata(tempo = "96 bpm"), blocks = emptyList())
        assertEquals("110", song.withTempo(110).metadata.tempo)
        assertEquals("96 bpm", song.withTempo(null).metadata.tempo)
    }

    private companion object {
        const val FILE_NAME = "a.cho"
        const val SETLIST = "s.setlist.json"
    }
}
