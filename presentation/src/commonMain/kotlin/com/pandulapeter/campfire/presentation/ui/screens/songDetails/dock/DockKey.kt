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
import com.pandulapeter.campfire.presentation.ui.playing.transpositionValue
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel

/**
 * What the dock's key chip says: the key the page's chords are in ([key], as the transposition stepper named it, its
 * semitones where the song names no key, null where there is neither) and the capo the song is played at where it is
 * not zero, which is always on screen. [opensPlaying] where the chip opens the Playing sheet, never in read only mode,
 * whose sheet would write overrides.
 */
@Immutable
internal data class DockKey(
    val key: String?,
    val capo: Int?,
    val opensPlaying: Boolean,
)

/**
 * The key chip of a song drawn as [model], set by [controls] (null in read only mode, which reads the values from the
 * song as the page shows it: the shapes' key where the chords are named by them, else the key that sounds). None with
 * the chords off, and none in read only mode where there is no key and no capo to say.
 */
internal fun dockKeyOf(controls: SongPlayingControls?, model: SongLyricsModel): DockKey? {
    if (!model.shouldShowChords) return null
    if (controls != null) {
        return DockKey(
            key = controls.key?.let { transpositionValue(it.transposition, it.key) },
            capo = controls.capo?.capo?.fret?.takeIf { it != 0 },
            opensPlaying = controls.onPlayingClicked != null,
        )
    }
    val key = (model.shapesKey ?: model.song.metadata.key)?.takeIf { it.isNotBlank() }
    val capo = model.song.metadata.capo?.takeIf { it != 0 && model.isCapoShown }
    return if (key == null && capo == null) null else DockKey(key = key, capo = capo, opensPlaying = false)
}
