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
 * semitones. They move what sounds, so they follow the key that sounds: the chip's own where it names it
 * ([transpositionOnChip]), else the subtitle's ([transpositionInSubtitle]). [opensPlaying] where the chip opens the Playing sheet, never in read only mode, whose sheet would write
 * overrides. [namesShapes] where [key] is the shapes fingered above a capo rather than the key that sounds, which the
 * title's subtitle then names beside it: the chip names it as the shapes ("Em shapes") rather than as the key, so that the bar never names
 * two keys for one song.
 */
@Immutable
internal data class KeyChipText(
    val key: String?,
    val capo: Int?,
    val opensPlaying: Boolean,
    val transposition: String? = null,
    val namesShapes: Boolean = false,
) {
    /** [transposition] beside the chip's key, which names what sounds. */
    val transpositionOnChip get() = transposition.takeUnless { namesShapes }

    /** [transposition] beside the subtitle's key, where the chip names the shapes and the subtitle what sounds. */
    val transpositionInSubtitle get() = transposition.takeIf { namesShapes }

    /**
     * What the chip's first line is: the words naming [key] (the shapes' where [namesShapes]) around it; the label alone where
     * the chip opens the sheet for a song with no key, so that it still names what it opens, never the label twice; else
     * none, the capo alone.
     */
    val keyLine
        get() = when {
            key != null -> KeyChipLine.LABELLED_KEY
            opensPlaying -> KeyChipLine.LABEL_ALONE
            else -> KeyChipLine.NONE
        }
}

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
            namesShapes = capo != null && controls.capo.namesShapes,
        )
    }
    val key = (model.shapesKey ?: model.song.metadata.key)?.takeIf { it.isNotBlank() }
    val capo = model.song.metadata.capo?.takeIf { it != 0 && model.isCapoShown }
    return if (key == null && capo == null) {
        null
    } else {
        KeyChipText(key = key, capo = capo, opensPlaying = false, namesShapes = key != null && model.shapesKey != null)
    }
}
