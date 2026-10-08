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

import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongCapoTest {

    private fun song(capo: Int?) = Song(
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
        capo = capo,
    )

    private val capos = Capos(library = mapOf(FILE_NAME to 4), bySetlist = mapOf(SETLIST to mapOf(FILE_NAME to 0)))

    @Test
    fun aSetlistReadsItsOwnEntryOnly() = assertEquals(EffectiveCapo(fret = 0, songFret = 2), effectiveCapo(song(2), SETLIST, capos))

    @Test
    fun theLibraryReadsTheOverride() = assertEquals(EffectiveCapo(fret = 4, songFret = 2), effectiveCapo(song(2), null, capos))

    @Test
    fun aSetlistWithoutAnEntryDoesNotInheritTheLibrary() =
        assertEquals(EffectiveCapo(fret = 2, songFret = 2), effectiveCapo(song(2), "other.setlist.json", capos))

    @Test
    fun aSongWithoutACapoIsPlayedWithoutOne() =
        assertEquals(EffectiveCapo(fret = 0, songFret = 0), effectiveCapo(song(null), "other.setlist.json", capos))

    @Test
    fun aCapoASetlistTookOffIsAnOverrideAndNotNothing() = assertFalse(effectiveCapo(song(2), SETLIST, capos).isDefault)

    @Test
    fun anOverrideEqualToTheSongsOwnIsNoOverride() = assertTrue(effectiveCapo(song(4), null, capos).isDefault)

    @Test
    fun aFileCapoOutOfRangeIsHeldWithinIt() = assertEquals(12, effectiveCapo(song(40), "other.setlist.json", capos).fret)

    @Test
    fun overridesAreSetAndRemovedByKey() {
        val changed = capos.with(CapoKey(FILE_NAME, SETLIST), null).with(CapoKey(FILE_NAME, null), 3)
        assertEquals(null, changed[FILE_NAME, SETLIST])
        assertEquals(3, changed[FILE_NAME, null])
    }

    @Test
    fun anOverrideRewritesTheCapoLine() {
        val song = ChordProSong(metadata = ChordProMetadata(capo = 2), blocks = emptyList())
        assertEquals(0, song.withCapo(0).metadata.capo)
        assertEquals(2, song.withCapo(null).metadata.capo)
    }

    private companion object {
        const val FILE_NAME = "a.cho"
        const val SETLIST = "s.setlist.json"
    }
}
