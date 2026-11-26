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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongCapoControl
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongKeyControl
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class KeyChipTextTest {

    @Test
    fun `the chip names the key the transposition stepper names and a capo other than zero and opens the sheet`() {
        val text = keyChipTextOf(controls(key = "Bm", capo = 2), model("{key: Am}"))
        assertEquals(KeyChipText(key = "Bm", capo = 2, opensPlaying = true), text)
    }

    @Test
    fun `a capo of zero is not named`() = assertNull(keyChipTextOf(controls(key = "Am", capo = 0), model("{key: Am}"))?.capo)

    @Test
    fun `a song naming no key is named by its semitones`() = assertEquals("+2", keyChipTextOf(controls(key = null, capo = 0, transposition = 2), model(""))?.key)

    @Test
    fun `read only the chip reads the song as the page shows it and opens nothing`() {
        assertEquals(KeyChipText(key = "Am", capo = 3, opensPlaying = false), keyChipTextOf(null, model("{key: Am}\n{capo: 3}")))
        assertEquals(KeyChipText(key = "Em", capo = 3, opensPlaying = false, namesShapes = true), keyChipTextOf(null, model("{key: Am}\n{capo: 3}", shapesKey = "Em")))
    }

    @Test
    fun `read only a song with neither a key nor a capo has no chip`() = assertNull(keyChipTextOf(null, model("")))

    @Test
    fun `a moved song names its semitones beside the key`() =
        assertEquals("+2", keyChipTextOf(controls(key = "Bm", capo = 0, transposition = 2), model("{key: Am}"))?.transposition)

    @Test
    fun `a song in its own key and one named by its semitones name no transposition beside the key`() {
        assertNull(keyChipTextOf(controls(key = "Am", capo = 0), model("{key: Am}"))?.transposition)
        assertNull(keyChipTextOf(controls(key = null, capo = 0, transposition = -3), model(""))?.transposition)
    }

    @Test
    fun `a named key is led by the Key label`() =
        assertEquals(KeyChipLine.LABELLED_KEY, keyChipTextOf(controls(key = "G", capo = 0, transposition = 1), model("{key: F#}"))?.keyLine)

    @Test
    fun `a song named by its semitones is led by the bare Key label rather than named as a key`() =
        assertEquals(KeyChipLine.LABELLED_SEMITONES, keyChipTextOf(controls(key = null, capo = 0, transposition = 2), model(""))?.keyLine)

    @Test
    fun `a song with no key whose chip opens the sheet reads the Key label alone`() =
        assertEquals(KeyChipLine.LABEL_ALONE, KeyChipText(key = null, capo = 2, opensPlaying = true).keyLine)

    @Test
    fun `read only a song with a capo and no key has only its capo line`() =
        assertEquals(KeyChipLine.NONE, keyChipTextOf(null, model("{capo: 3}"))?.keyLine)

    @Test
    fun `a key naming the shapes above a capo is labelled as the shapes`() {
        assertEquals(true, keyChipTextOf(controls(key = "Em", capo = 2, namesShapes = true), model("{key: Em}"))?.namesShapes)
        assertEquals(true, keyChipTextOf(null, model("{key: F#m}\n{capo: 2}", shapesKey = "Em"))?.namesShapes)
    }

    @Test
    fun `a key naming what sounds is labelled as the key`() {
        assertEquals(false, keyChipTextOf(controls(key = "F#m", capo = 2), model("{key: Em}"))?.namesShapes)
        assertEquals(false, keyChipTextOf(null, model("{key: Em}\n{capo: 2}"))?.namesShapes)
    }

    @Test
    fun `with no capo on the key is labelled as the key`() =
        assertEquals(false, keyChipTextOf(controls(key = "Em", capo = 0, namesShapes = true), model("{key: Em}"))?.namesShapes)

    @Test
    fun `the semitones follow the key that sounds on the chip or else in the subtitle`() {
        val sounding = keyChipTextOf(controls(key = "Bm", capo = 2, transposition = 2), model("{key: Am}"))
        assertEquals("+2" to null, sounding?.transpositionOnChip to sounding?.transpositionInSubtitle)
        val shapes = keyChipTextOf(controls(key = "Bm", capo = 2, transposition = 2, namesShapes = true), model("{key: Am}"))
        assertEquals(null to "+2", shapes?.transpositionOnChip to shapes?.transpositionInSubtitle)
    }

    @Test
    fun `the chords switched off take the chip`() = assertNull(keyChipTextOf(controls(key = "Am", capo = 2), model("{key: Am}", shouldShowChords = false)))

    private fun model(text: String, shapesKey: String? = null, shouldShowChords: Boolean = true) = SongLyricsModel(
        song = ChordProParser.parse(text),
        sections = emptyList(),
        isCut = false,
        shouldShowChords = shouldShowChords,
        shapesKey = shapesKey,
    )

    private fun controls(key: String?, capo: Int, transposition: Int = 0, namesShapes: Boolean = false) = SongPlayingControls(
        key = SongKeyControl(transposition = transposition, isDefault = transposition == 0, key = key, labels = emptyList(), onStep = {}, onReset = {}),
        capo = SongCapoControl(capo = EffectiveCapo(fret = capo, songFret = 0), namesShapes = namesShapes, shapesKey = null, onStep = {}, onReset = {}),
        tempo = null,
        onPlayingClicked = {},
    )
}
