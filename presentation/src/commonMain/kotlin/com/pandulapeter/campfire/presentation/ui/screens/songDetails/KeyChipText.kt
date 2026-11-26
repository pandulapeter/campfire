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
import com.pandulapeter.campfire.presentation.ui.playing.transpositionLabel
import com.pandulapeter.campfire.presentation.ui.playing.transpositionValue
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel

/**
 * What the app bar's key chip ([KeyChip]) says: the key the page's chords are in ([key], as the transposition stepper
 * named it, its semitones where the song names no key, null where there is neither) and the capo the song is played at
 * where it is not zero, which is always on screen. [transposition] is the semitones the song is moved by ("+2") beside a
 * named key, so that a moved song says so without the sheet; null where it is not moved, or where [key] already is the
 * semitones. [opensPlaying] where the chip opens the Playing sheet, never in read only mode, whose sheet would write
 * overrides.
 */
@Immutable
internal data class KeyChipText(
    val key: String?,
    val capo: Int?,
    val opensPlaying: Boolean,
    val transposition: String? = null,
)

/**
 * The key chip of a song drawn as [model], set by [controls] (null in read only mode, which reads the values from the
 * song as the page shows it: the shapes' key where the chords are named by them, else the key that sounds). None with
 * the chords off, and none in read only mode where there is no key and no capo to say.
 */
internal fun keyChipTextOf(controls: SongPlayingControls?, model: SongLyricsModel): KeyChipText? {
    if (!model.shouldShowChords) return null
    if (controls != null) {
        val keyControl = controls.key
        val capo = controls.capo?.capo?.fret?.takeIf { it != 0 }
        return KeyChipText(
            key = keyControl?.let { transpositionValue(it.transposition, it.key) },
            capo = capo,
            opensPlaying = controls.onPlayingClicked != null,
            transposition = keyControl?.takeIf { it.transposition != 0 && !it.key.isNullOrBlank() }?.let { transpositionLabel(it.transposition, null) },
        )
    }
    val key = (model.shapesKey ?: model.song.metadata.key)?.takeIf { it.isNotBlank() }
    val capo = model.song.metadata.capo?.takeIf { it != 0 && model.isCapoShown }
    return if (key == null && capo == null) null else KeyChipText(key = key, capo = capo, opensPlaying = false)
}
