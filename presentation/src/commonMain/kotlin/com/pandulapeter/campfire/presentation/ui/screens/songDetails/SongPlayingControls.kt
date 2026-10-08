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

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo

/**
 * What sets the four values a song is played by, from the song's own first section, see [SongPlayingControlsRow]. The
 * song details screen builds it for the song on screen and only outside read only mode: performance mode and a song
 * read from an archived setlist get the plain line of text instead, since neither changes anything about a song.
 *
 * Three of the four are set where the song is read — a transposition, a capo and a tempo belong to the setlist the
 * band plays it in, or to this device for a song opened from the library — while what the file itself declares, the
 * time signature among it, is edited in the editing menu's "Song defaults" sheet (`SongPlayingDialog`).
 */
@Immutable
internal class SongPlayingControls(
    /** Null for a song with nothing to transpose, whose key is then only read, and with the chords switched off. */
    val key: SongKeyControl?,
    /** Null with the chords switched off. */
    val capo: SongCapoControl?,
    /** Null with the metronome switched off. */
    val tempo: SongTempoControl?,
    /**
     * The time signature as the file writes it, or the one the click counts the bar by where it names none. Null with
     * the metronome switched off, since the bar it counts is the click's.
     */
    val timeSignature: String?,
)

/** The transposition, which the key reads: the amount, the key it takes the song to, and the stepper's two ends. */
@Immutable
internal class SongKeyControl(
    val transposition: Int,
    val key: String?,
    val onStep: (semitones: Int) -> Unit,
    val onReset: () -> Unit,
)

@Immutable
internal class SongCapoControl(
    val capo: EffectiveCapo,
    val onStep: (frets: Int) -> Unit,
    val onReset: () -> Unit,
)

/** The tempo the click would play at: the same stepper and Tap button the overflow menu's row had. */
@Immutable
internal class SongTempoControl(
    val tempo: EffectiveTempo,
    val onStep: (delta: Int) -> Unit,
    val onTapped: (bpm: Int) -> Unit,
    val onReset: () -> Unit,
)
