/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo
import kotlin.test.Test
import kotlin.test.assertEquals

class ShownPlayingOverridesTest {

    private val capoOverride = EffectiveCapo(fret = 3, songFret = 0)
    private val tempoOverride = EffectiveTempo(bpm = 100, source = EffectiveTempo.Source.SETLIST, songBpm = 96)

    private fun shown(hasChords: Boolean = true, capo: EffectiveCapo = capoOverride, areChordsEnabled: Boolean = true, isMetronomeEnabled: Boolean = true) =
        shownPlayingOverrides(
            song = song(hasChords),
            transposition = 2,
            capo = capo,
            tempo = tempoOverride,
            areChordsEnabled = areChordsEnabled,
            isMetronomeEnabled = isMetronomeEnabled,
        )

    @Test
    fun `every override is shown with every feature on`() =
        assertEquals(ShownPlayingOverrides(transposition = 2, capo = capoOverride, tempo = tempoOverride), shown())

    @Test
    fun `chords off hide the transposition and the capo`() =
        assertEquals(ShownPlayingOverrides(transposition = 0, capo = null, tempo = tempoOverride), shown(areChordsEnabled = false))

    @Test
    fun `the metronome off hides the tempo`() =
        assertEquals(ShownPlayingOverrides(transposition = 2, capo = capoOverride, tempo = null), shown(isMetronomeEnabled = false))

    @Test
    fun `a song without chords has no transposition to name but keeps its capo`() =
        assertEquals(ShownPlayingOverrides(transposition = 0, capo = capoOverride, tempo = tempoOverride), shown(hasChords = false))

    @Test
    fun `a capo at the song's own fret is no override`() =
        assertEquals(null, shown(capo = EffectiveCapo(fret = 2, songFret = 2)).capo)

    @Test
    fun `a tempo read from the file is no override`() = assertEquals(
        null,
        shownPlayingOverrides(
            song = song(hasChords = true),
            transposition = 0,
            capo = capoOverride,
            tempo = EffectiveTempo(bpm = 96, source = EffectiveTempo.Source.FILE, songBpm = 96),
            areChordsEnabled = true,
            isMetronomeEnabled = true,
        ).tempo,
    )

    private fun song(hasChords: Boolean) = Song(
        fileName = "song.cho", title = "Song", artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = hasChords,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
