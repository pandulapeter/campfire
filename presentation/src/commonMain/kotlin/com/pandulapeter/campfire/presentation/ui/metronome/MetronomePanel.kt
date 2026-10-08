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
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * The least of a metronome that is still one - the bar as it is heard, with its accents tapped on it, and play and stop
 * at the end of the row, where the thumb holding the phone reaches it - and the one shape the click takes on both
 * screens that have one, so that it is started, stopped and accented the same way on either.
 *
 * On the song details screen it is a panel inside the app bar, under its title row, that the bar's own button shows and
 * hides ([CampfireViewModel.toggleMetronomePanel]): the click is something a song is played to there, the tempo is
 * already in the song's own first section right under the bar, and being part of the bar rather than something floating
 * under it, it stays where it is while the song is read and covers none of it. On the Metronome tab it is the header,
 * pinned above everything else the tab sets and never hidden ([isVisible] always true), so that the click can be
 * stopped wherever the page has been scrolled to.
 *
 * @param isProminent Draws the row and the button at the size of an instrument rather than of a bar's own row, and at
 * a settings page's margins: the tab's, where the panel is what the screen is for and heads the rows under it.
 * @param visibleState Where the panel's showing and hiding is followed, for a caller that has to know when it has
 *   come to rest: the song details screen, whose pages decide their grid only once the room the panel leaves them has
 *   stopped changing. Its target is kept at [isVisible] here.
 */
@Composable
internal fun MetronomePanel(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    isVisible: Boolean,
    isProminent: Boolean = false,
    contentPadding: PaddingValues,
    visibleState: MutableTransitionState<Boolean>? = null,
) = AnimatedVisibility(
    modifier = modifier,
    visibleState = rememberPanelVisibleState(given = visibleState, isVisible = isVisible),
    // The bar grows a row rather than something sliding out from behind it, and its edge never passes over the
    // controls: they are squashed towards the top on the very spec the height follows, so at every frame they are
    // exactly as tall as the room the bar has for them. A spring would not do here, since the size's and the scale's
    // would settle against different thresholds and drift apart.
    enter = expandVertically(
        animationSpec = tween(PANEL_ANIMATION_DURATION, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top,
    ),
    exit = shrinkVertically(
        animationSpec = tween(PANEL_ANIMATION_DURATION, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.Top,
    ),
) {
    val contentProgress = transition.animateFloat(
        transitionSpec = { tween(PANEL_ANIMATION_DURATION, easing = FastOutSlowInEasing) },
        label = "songMetronomePanelContent",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val songsBeingRenamed by viewModel.songsBeingRenamed.collectAsStateWithLifecycle()
    // The bar the row draws is the one the click counts, which is the song's time signature where it declares one (that of
    // the stretch of it the page is on, where it changes further down), and
    // which is what the accents it is tapped are stored under, so a song in 6/8 is accented as the tab's 6/8 is. The
    // tempo it plays at is nothing this panel shows, so none of what overrides it is read here. A song being renamed is
    // still the click's song, and its bar is still its own, for the moment the library no longer has the old name.
    val timeSignature = when (val context = viewModel.metronomeContext) {
        MetronomeContext.Standalone -> settings.timeSignatureOrDefault
        is MetronomeContext.Song -> context.timing?.timeSignature
            ?: (songsByFileName[context.songFileName] ?: songsBeingRenamed[context.songFileName]).timeSignatureOrDefault
    }
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = if (isProminent) PROMINENT_PANEL_HORIZONTAL_PADDING else PANEL_START_PADDING
    val endPadding = if (isProminent) PROMINENT_PANEL_HORIZONTAL_PADDING else PANEL_END_PADDING
    val height = if (isProminent) PROMINENT_PANEL_HEIGHT else PANEL_HEIGHT
    Row(
        modifier = Modifier
            .graphicsLayer {
                alpha = contentProgress.value
                scaleY = contentProgress.value
                transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
            }
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + startPadding,
                end = contentPadding.calculateEndPadding(layoutDirection) + endPadding,
                bottom = PANEL_PADDING,
            )
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_PADDING),
    ) {
        BeatRow(
            modifier = Modifier.weight(1f),
            beatLevels = settings.beatLevelsOf(timeSignature),
            beats = viewModel.metronomeBeats,
            isPlaying = isPlaying,
            isFlashEnabled = settings.isVisualBeatEnabled,
            blockHeight = if (isProminent) PROMINENT_PANEL_HEIGHT else PANEL_BEAT_HEIGHT,
            blockGap = if (isProminent) PROMINENT_PANEL_BEAT_GAP else PANEL_BEAT_GAP,
            onBeatLevelsChanged = { levels -> viewModel.updateMetronomeSettings { withBeatLevels(timeSignature, levels) } },
        )
        val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
        FilledIconButton(
            modifier = Modifier
                .then(if (isProminent) Modifier.size(PROMINENT_PANEL_HEIGHT) else Modifier)
                // The row already squashes it vertically, so this is what keeps the button round as it scales down.
                .graphicsLayer { scaleX = contentProgress.value }
                .semantics { contentDescription = label },
            onClick = viewModel::toggleMetronome,
        ) {
            PlayStopMark(
                isPlaying = isPlaying,
                size = if (isProminent) PROMINENT_PLAY_MARK_SIZE else PLAY_MARK_SIZE,
            )
        }
    }
}

/** The [given] state, or one of the panel's own where there is none, either way headed for [isVisible]. */
@Composable
private fun rememberPanelVisibleState(given: MutableTransitionState<Boolean>?, isVisible: Boolean): MutableTransitionState<Boolean> {
    // Remembered whether or not it is used, so that the slots stay the same whichever state is given.
    val own = remember { MutableTransitionState(isVisible) }
    return (given ?: own).apply { targetState = isVisible }
}

private const val PANEL_ANIMATION_DURATION = 250
private val PANEL_HEIGHT = 48.dp
private val PANEL_PADDING = 12.dp

/**
 * The panel's margins in the song details screen's app bar, which put the beats where the song's own text starts and
 * the button's circle on the keyline of the step buttons under it, 8dp from the end of the screen: the button is a
 * 48dp touch target around a 40dp circle, so its end padding is what leaves the other 4dp.
 */
private val PANEL_START_PADDING = 8.dp
private val PANEL_END_PADDING = 4.dp

/**
 * How tall an accent is drawn here, which is also the column each beat is tapped in: as much of the panel's own height
 * as the row can take while the blocks still read as something inside a bar rather than as the bar itself.
 */
private val PANEL_BEAT_HEIGHT = 32.dp
private val PANEL_BEAT_GAP = 3.dp
private val PLAY_MARK_SIZE = 24.dp

/**
 * The panel as the Metronome tab's header: the accents and the button as tall as each other, a thumb wide, and starting
 * and ending where the rows of the page under them do.
 */
private val PROMINENT_PANEL_HEIGHT = 56.dp
private val PROMINENT_PANEL_HORIZONTAL_PADDING = 16.dp
private val PROMINENT_PANEL_BEAT_GAP = 6.dp
private val PROMINENT_PLAY_MARK_SIZE = 32.dp
