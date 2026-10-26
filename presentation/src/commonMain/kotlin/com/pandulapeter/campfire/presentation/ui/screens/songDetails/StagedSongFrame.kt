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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.songView.LeavingParts
import com.pandulapeter.campfire.presentation.ui.songView.SongChange
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** What a song page draws that a change of how the song is shown ([SongLook]) changes. */
internal class SongFrame(
    val model: SongLyricsModel,
    val playingControls: SongPlayingControls?,
    val chordDiagrams: ChordDiagrams?,
    val scaleSuggestions: ScaleSuggestions?,
    val shouldShowTempo: Boolean,
)

/** How a song is shown: the song preferences, and the metronome, whose switching is narrated as a [SongChange]. */
internal data class SongLook(
    val shouldShowChords: Boolean,
    val hasChordDiagrams: Boolean,
    val hasScaleSuggestions: Boolean,
    val showsDegrees: Boolean,
    val chordSpelling: UserPreferences.ChordSpelling,
    val shouldNumberSections: Boolean,
    val shouldShowTempo: Boolean,
)

/** What a change from the [shown] look to the [target] one takes off the page. */
internal fun leavingPartsOf(shown: SongLook, target: SongLook) = LeavingParts(
    chords = shown.shouldShowChords && !target.shouldShowChords,
    chordsSection = shown.hasChordDiagrams && !target.hasChordDiagrams,
    scalesSection = shown.hasScaleSuggestions && !target.hasScaleSuggestions,
    instrumentPill = shown.hasChordDiagrams && !target.hasChordDiagrams,
    timing = shown.shouldShowTempo && !target.shouldShowTempo,
)

/**
 * The [frame] to draw, staging every change of its [look] as the [change] says: the frame before it is held while what
 * the change takes away fades out, the new one is drawn once its model is built ([isModelBuilt]) and glides into place,
 * and what it brings fades in after. Any other change of the frame is drawn at once, as is a change of the look that
 * comes back to the one shown before it got under way.
 */
@Composable
internal fun rememberStagedSongFrame(
    frame: SongFrame,
    look: SongLook,
    isModelBuilt: Boolean,
    change: SongChange,
): SongFrame {
    val stage = remember { SongStage(look, frame) }
    val isHolding = look != stage.shownLook
    if (!isHolding) stage.frame = frame
    val latestIsModelBuilt by rememberUpdatedState(isModelBuilt)
    val leavingSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val arrivingSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(look) {
        change.leavingAlpha.snapTo(1f)
        change.arrivingAlpha.snapTo(1f)
        change.areChordsArriving = false
        change.leaving = null
        change.phase = SongChange.Phase.IDLE
        if (look == stage.shownLook) return@LaunchedEffect
        val leaving = leavingPartsOf(stage.shownLook, look).takeUnless { it.isEmpty }
        change.leaving = leaving
        change.phase = SongChange.Phase.LEAVING
        if (leaving != null) change.leavingAlpha.animateTo(0f, leavingSpec)
        snapshotFlow { latestIsModelBuilt }.first { it }
        // One snapshot: the frame drawn from here on is the new one, and what it brings is composed knowing that it
        // arrived with the change, so it waits to fade in.
        change.arrivingAlpha.snapTo(0f)
        change.areChordsArriving = !stage.shownLook.shouldShowChords && look.shouldShowChords
        change.leaving = null
        change.phase = SongChange.Phase.MOVING
        stage.shownLook = look
        change.leavingAlpha.snapTo(1f)
        delay(MOVING_MILLIS)
        change.phase = SongChange.Phase.ARRIVING
        change.arrivingAlpha.animateTo(1f, arrivingSpec)
        change.areChordsArriving = false
        change.phase = SongChange.Phase.IDLE
    }
    return if (isHolding) stage.frame else frame
}

/** The look on the page, which is state, and the frame last drawn of it, which is written while composing. */
private class SongStage(look: SongLook, var frame: SongFrame) {

    var shownLook by mutableStateOf(look)
}

/**
 * How long the sections glide before what the change brings starts fading in: most of the way of the spring they move on,
 * so that it arrives in a page that has come to rest rather than among sections still on their way.
 */
private const val MOVING_MILLIS = 250L
