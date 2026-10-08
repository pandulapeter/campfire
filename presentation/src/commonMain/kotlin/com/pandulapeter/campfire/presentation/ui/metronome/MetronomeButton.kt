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

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_metronome
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_hide
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_show
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.KEY_SEPARATOR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource

/**
 * The song details screen's metronome: the mark that shows and hides the panel the click is played from, in the primary
 * color while the panel is up, as a control that is switched on, and pulsing on every heard beat (a little larger on an
 * accent) unless the Animate switch is off. It is not the second accent the beat row under it is drawn in: the app's own
 * palette darkens that orange for text on its light half, and a whole filled mark in it reads as red rather than as the
 * fire.
 * While the panel is up that pulse is the growing alone, the mark being in its color already and the panel's own beat
 * row being right under it. Its pendulum swings with the click, see [rememberMetronomeIconBeat]. The content description
 * says the tempo the click would start at, since that is all a screen reader user would otherwise not know.
 */
@Composable
internal fun MetronomeButton(
    modifier: Modifier = Modifier,
    isPanelShown: Boolean,
    playback: StateFlow<MetronomePlayback>,
    bpm: Int,
    beats: Flow<MetronomeBeat>,
    isFlashEnabled: Boolean,
    onClick: () -> Unit,
) {
    val baseColor by animateColorAsState(if (isPanelShown) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    IconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        MetronomeIcon(
            beat = rememberMetronomeIconBeat(playback = playback, beats = beats, isEnabled = isFlashEnabled),
            contentDescription = metronomePanelLabel(isPanelShown = isPanelShown, bpm = bpm),
            tint = baseColor,
        )
    }
}


/** [MetronomeButton] as an entry of the song details overflow menu, for a bar that has no room for the button. */
@Composable
internal fun metronomeAction(
    isPanelShown: Boolean,
    bpm: Int,
    onClick: () -> Unit,
) = ActionsMenuItem(
    title = metronomePanelLabel(isPanelShown = isPanelShown, bpm = bpm),
    icon = painterResource(Res.drawable.ic_metronome),
    key = METRONOME_ACTION_KEY,
    onClick = onClick,
)

private const val METRONOME_ACTION_KEY = "metronome"

/** What showing or hiding the panel is called, with the tempo a click started from it would play at while it is hidden. */
@Composable
private fun metronomePanelLabel(isPanelShown: Boolean, bpm: Int) = if (isPanelShown) {
    stringResource(Res.string.song_details_metronome_hide)
} else {
    "${stringResource(Res.string.song_details_metronome_show)} $KEY_SEPARATOR ${stringResource(Res.string.song_details_tempo, bpm.toString())}"
}
