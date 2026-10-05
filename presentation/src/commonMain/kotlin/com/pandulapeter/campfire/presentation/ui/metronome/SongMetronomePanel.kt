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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.screens.metronome.BeatRow

/**
 * The metronome of the song details screen: a panel inside the app bar, under its title row, that the bar's own button
 * shows and hides ([CampfireViewModel.toggleMetronomePanel]). Here the click is something a song is played to, so the
 * panel holds the least of a metronome that is still one - the bar as it is heard, with its accents drawn on it as on
 * the Metronome tab, and play and stop at the end of the row, where the thumb holding the phone reaches it - and
 * nothing else: the tempo is already in the song's own first section, right under the bar, and how the click sounds,
 * its subdivision and its volume, is the tab's. It is part of the bar rather than something floating under it, so it
 * stays where it is while the song is read and covers none of it.
 */
@Composable
internal fun SongMetronomePanel(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    isVisible: Boolean,
    contentPadding: PaddingValues,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    // The bar grows a row rather than something sliding out from behind it.
    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
) {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    // The bar the row draws is the one the click counts, which is the song's time signature where it declares one, and
    // which is what the accents it is tapped are stored under, so a song in 6/8 is accented as the tab's 6/8 is. The
    // tempo it plays at is nothing this panel shows, so none of what overrides it is read here.
    val timeSignature = when (val context = viewModel.metronomeContext) {
        MetronomeContext.Standalone -> settings.timeSignatureOrDefault
        is MetronomeContext.Song -> songsByFileName[context.songFileName].timeSignatureOrDefault
    }
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + PANEL_PADDING,
                end = contentPadding.calculateEndPadding(layoutDirection) + PANEL_PADDING,
                bottom = PANEL_PADDING,
            )
            .height(PANEL_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_PADDING),
    ) {
        BeatRow(
            modifier = Modifier.weight(1f),
            beatLevels = settings.beatLevelsOf(timeSignature),
            beats = viewModel.metronomeBeats,
            isPlaying = isPlaying,
            isFlashEnabled = settings.isVisualBeatEnabled,
            blockHeight = PANEL_BEAT_HEIGHT,
            blockGap = PANEL_BEAT_GAP,
            onBeatLevelsChanged = { levels -> viewModel.updateMetronomeSettings { withBeatLevels(timeSignature, levels) } },
        )
        val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
        FilledIconButton(
            modifier = Modifier.semantics { contentDescription = label },
            onClick = viewModel::toggleMetronome,
        ) {
            PlayStopMark(isPlaying = isPlaying)
        }
    }
}

private val PANEL_HEIGHT = 48.dp
private val PANEL_PADDING = 12.dp

/**
 * How tall an accent is drawn here, which is also the column each beat is tapped in: as much of the panel's own height
 * as the row can take while the blocks still read as something inside a bar rather than as the bar itself.
 */
private val PANEL_BEAT_HEIGHT = 32.dp
private val PANEL_BEAT_GAP = 3.dp
