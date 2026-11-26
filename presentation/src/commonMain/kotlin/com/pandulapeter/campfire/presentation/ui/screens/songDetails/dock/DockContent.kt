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

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel

/**
 * What one page of the song details pager hands the dock to draw while it is the page the pager is on or heading for:
 * the song's [model] as the page drew it, and what the song's first, Chords, and Scales sections were drawn with before
 * they left the song for the dock.
 *
 * @param playingControls What sets the key, the capo, the tempo, and the time signature, null in read only mode, whose
 *   key chip (the app bar's, which reads it from here) reads them from the song instead.
 * @param tempo What the tempo chip says and opens, null with the metronome switched off and in an import preview, which
 *   has no metronome.
 * @param chordDiagrams What the diagrams are drawn with, null with the diagrams off.
 * @param scaleSuggestions What the scale chips open, null with the scale suggestions off.
 */
@Immutable
internal data class DockContent(
    val model: SongLyricsModel,
    val playingControls: SongPlayingControls?,
    val tempo: DockTempo?,
    val chordDiagrams: ChordDiagrams?,
    val scaleSuggestions: ScaleSuggestions?,
)
