/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_hide
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_show
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.components.KEY_SEPARATOR
import com.pandulapeter.campfire.presentation.ui.components.controls.SongToolButton
import com.pandulapeter.campfire.presentation.ui.components.controls.songToolIconSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The song details screen's metronome, next to the tempo and the time signature it plays: the mark that shows and hides
 * the panel the click is played from, a toggle drawn as switched on while the panel is up, and pulsing on every heard
 * beat (a little larger on an accent) unless the Animate switch is off. Its pendulum swings with the click, see
 * [rememberMetronomeIconBeat]. The content description says the tempo the click would start at, since that is all a
 * screen reader user would otherwise not know.
 */
@Composable
internal fun MetronomeButton(
    modifier: Modifier = Modifier,
    isPanelShown: Boolean,
    playback: StateFlow<MetronomePlayback>,
    bpm: Int,
    beats: Flow<MetronomeBeat>,
    isFlashEnabled: Boolean,
    height: Dp,
    fontScale: Float,
    onClick: () -> Unit,
) = SongToolButton(
    modifier = modifier,
    height = height,
    isSelected = isPanelShown,
    onClick = onClick,
) {
    MetronomeIcon(
        modifier = Modifier.size(songToolIconSize(fontScale)),
        beat = rememberMetronomeIconBeat(playback = playback, beats = beats, isEnabled = isFlashEnabled),
        contentDescription = metronomePanelLabel(isPanelShown = isPanelShown, bpm = bpm),
    )
}

/** What showing or hiding the panel is called, with the tempo a click started from it would play at while it is hidden. */
@Composable
private fun metronomePanelLabel(isPanelShown: Boolean, bpm: Int) = if (isPanelShown) {
    stringResource(Res.string.song_details_metronome_hide)
} else {
    "${stringResource(Res.string.song_details_metronome_show)} $KEY_SEPARATOR ${stringResource(Res.string.song_details_tempo, bpm.toString())}"
}
