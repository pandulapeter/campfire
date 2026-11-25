/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongCapoControl
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongKeyControl
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class DockKeyTest {

    @Test
    fun `the chip names the key the transposition stepper names and a capo other than zero and opens the sheet`() {
        val dockKey = dockKeyOf(controls(key = "Bm", capo = 2), model("{key: Am}"))
        assertEquals(DockKey(key = "Bm", capo = 2, opensPlaying = true), dockKey)
    }

    @Test
    fun `a capo of zero is not named`() = assertNull(dockKeyOf(controls(key = "Am", capo = 0), model("{key: Am}"))?.capo)

    @Test
    fun `a song naming no key is named by its semitones`() = assertEquals("+2", dockKeyOf(controls(key = null, capo = 0, transposition = 2), model(""))?.key)

    @Test
    fun `read only the chip reads the song as the page shows it and opens nothing`() {
        assertEquals(DockKey(key = "Am", capo = 3, opensPlaying = false), dockKeyOf(null, model("{key: Am}\n{capo: 3}")))
        assertEquals(DockKey(key = "Em", capo = 3, opensPlaying = false), dockKeyOf(null, model("{key: Am}\n{capo: 3}", shapesKey = "Em")))
    }

    @Test
    fun `read only a song with neither a key nor a capo has no chip`() = assertNull(dockKeyOf(null, model("")))

    @Test
    fun `a moved song names its semitones beside the key`() =
        assertEquals("+2", dockKeyOf(controls(key = "Bm", capo = 0, transposition = 2), model("{key: Am}"))?.transposition)

    @Test
    fun `a song in its own key and one named by its semitones name no transposition beside the key`() {
        assertNull(dockKeyOf(controls(key = "Am", capo = 0), model("{key: Am}"))?.transposition)
        assertNull(dockKeyOf(controls(key = null, capo = 0, transposition = -3), model(""))?.transposition)
    }

    @Test
    fun `a capo names the key of the shapes fingered above it where the key names what sounds`() {
        assertEquals("Am", dockKeyOf(controls(key = "Bm", capo = 2, shapesKey = "Am"), model("{key: Am}"))?.shapesKey)
        assertNull(dockKeyOf(controls(key = "Am", capo = 0, shapesKey = "Am"), model("{key: Am}"))?.shapesKey)
    }

    @Test
    fun `the chords switched off take the chip`() = assertNull(dockKeyOf(controls(key = "Am", capo = 2), model("{key: Am}", shouldShowChords = false)))

    private fun model(text: String, shapesKey: String? = null, shouldShowChords: Boolean = true) = SongLyricsModel(
        song = ChordProParser.parse(text),
        sections = emptyList(),
        isCut = false,
        shouldShowChords = shouldShowChords,
        shapesKey = shapesKey,
    )

    private fun controls(key: String?, capo: Int, transposition: Int = 0, shapesKey: String? = null) = SongPlayingControls(
        key = SongKeyControl(transposition = transposition, isDefault = transposition == 0, key = key, labels = emptyList(), onStep = {}, onReset = {}),
        capo = SongCapoControl(capo = EffectiveCapo(fret = capo, songFret = 0), namesShapes = false, shapesKey = shapesKey, onStep = {}, onReset = {}),
        tempo = null,
        onPlayingClicked = {},
    )
}
