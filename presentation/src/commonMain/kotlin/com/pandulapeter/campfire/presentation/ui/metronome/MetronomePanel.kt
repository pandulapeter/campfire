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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_reset
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.metronome.BeatRow
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * Lays the app out next to the metronome panel: a bar across the top of a window taller than it is wide, a column down
 * the end edge of one wider than it is tall, so that the panel takes the dimension the window has the most of. The
 * panel is there for as long as a click plays, whatever screen is showing, and always on the Metronome tab, whose
 * screen is the rest of the instrument; it expands in and shrinks away, and [content] is given what is left.
 *
 * The panel covers the insets of the edge it is on, so [content] is handed those as insets to leave out of the paddings
 * it works out by hand, and they are consumed for everything that pads by modifier. They follow the panel's size frame
 * by frame: a panel halfway in covers the status bar only halfway.
 */
@Composable
internal fun MetronomePanelScaffold(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    content: @Composable (panelInsets: WindowInsets) -> Unit,
) {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isVisible = playback is MetronomePlayback.Playing || viewModel.backStack.lastOrNull() == CampfireDestination.Metronome
    val isVertical = LocalWindowInfo.current.containerDpSize.let { it.width > it.height }
    val panelInsets = remember { MetronomePanelInsets() }
    Layout(
        modifier = modifier,
        content = {
            // AnimatedVisibility emits nothing once it is hidden, and the layout counts on two children.
            Box {
                AnimatedVisibility(
                    visible = isVisible,
                    // From the window's edge, so that the panel slides in from it rather than unrolling towards it.
                    enter = fadeIn() + if (isVertical) expandHorizontally(expandFrom = Alignment.Start) else expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + if (isVertical) shrinkHorizontally(shrinkTowards = Alignment.Start) else shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    MetronomePanel(
                        viewModel = viewModel,
                        isPlaying = playback is MetronomePlayback.Playing,
                        isVertical = isVertical,
                    )
                }
            }
            Box(modifier = Modifier.consumeWindowInsets(panelInsets)) {
                content(panelInsets)
            }
        },
    ) { (panelMeasurable, contentMeasurable), constraints ->
        val panel = panelMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        // Written before the content is measured, which is where the insets are read: by the paddings while they are
        // laid out, and by the screens the navigation scaffold composes during its own measure pass.
        panelInsets.top = if (isVertical) 0 else panel.height
        panelInsets.end = if (isVertical) panel.width else 0
        val contentPlaceable = contentMeasurable.measure(
            if (isVertical) constraints.offset(horizontal = -panel.width) else constraints.offset(vertical = -panel.height)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (isVertical) {
                contentPlaceable.placeRelative(x = 0, y = 0)
                panel.placeRelative(x = constraints.maxWidth - panel.width, y = 0)
            } else {
                panel.placeRelative(x = 0, y = 0)
                contentPlaceable.placeRelative(x = 0, y = panel.height)
            }
        }
    }
}

/**
 * What the metronome panel covers of the window, in pixels. State, written by the scaffold as it measures the panel
 * and read wherever an inset is turned into a padding, so that the panel growing lays those out again and nothing else.
 */
@Stable
private class MetronomePanelInsets : WindowInsets {
    var top by mutableIntStateOf(0)
    var end by mutableIntStateOf(0)

    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Rtl) end else 0

    override fun getTop(density: Density) = top

    override fun getRight(density: Density, layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) end else 0

    override fun getBottom(density: Density) = 0
}

/**
 * The least of a metronome that is still one: play and stop, the bar as it is heard, and the tempo of whatever the
 * click plays for ([MetronomeContext]) - the song on screen, or the Metronome tab's own - with its stepper. The stepper
 * writes where the song details screen's does, so it is left out where that screen has none: for a song in performance
 * mode or opened from an archived setlist, which keep the number alone.
 */
@Composable
private fun MetronomePanel(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    isPlaying: Boolean,
    isVertical: Boolean,
) {
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val tempos by viewModel.tempos.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val context = viewModel.metronomeContext
    val pattern = metronomePatternOf(context = context, settings = settings, songOf = songsByFileName::get, tempos = tempos)
    val songContext = context as? MetronomeContext.Song
    val isTempoDefault = if (songContext == null) {
        pattern.bpm == MetronomePattern.DEFAULT_BPM
    } else {
        effectiveTempo(
            song = songsByFileName[songContext.songFileName],
            setlistFileName = songContext.setlistFileName,
            tempos = tempos,
            songFileName = songContext.songFileName,
        ).isDefault
    }
    val isTempoEditable = songContext == null ||
            !(isPerformanceModeEnabled || setlists.any { it.fileName == songContext.setlistFileName && it.isArchived })
    val playButton = @Composable {
        val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
        FilledIconButton(
            modifier = Modifier.semantics { contentDescription = label },
            onClick = viewModel::toggleMetronome,
        ) {
            PlayStopMark(isPlaying = isPlaying)
        }
    }
    val beatRow = @Composable { beatRowModifier: Modifier ->
        BeatRow(
            modifier = beatRowModifier,
            beatLevels = pattern.beatLevels,
            beats = viewModel.metronomeBeats,
            isPlaying = isPlaying,
            isFlashEnabled = settings.isVisualBeatEnabled,
            blockHeight = PANEL_BEAT_HEIGHT,
            blockGap = PANEL_BEAT_GAP,
            onBeatLevelsChanged = null,
        )
    }
    val tempo = @Composable {
        AnimatedContent(
            targetState = isTempoEditable,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { isEditable ->
            if (isEditable) {
                Stepper(
                    value = pattern.bpm.toString(),
                    isDefault = isTempoDefault,
                    decreaseIcon = painterResource(Res.drawable.ic_subtract),
                    decreaseLabel = stringResource(Res.string.metronome_tempo_decrease),
                    canDecrease = pattern.bpm > MetronomePattern.BPM_RANGE.first,
                    onDecrease = { viewModel.stepMetronomeTempo(-1) },
                    increaseIcon = painterResource(Res.drawable.ic_add),
                    increaseLabel = stringResource(Res.string.metronome_tempo_increase),
                    canIncrease = pattern.bpm < MetronomePattern.BPM_RANGE.last,
                    onIncrease = { viewModel.stepMetronomeTempo(1) },
                    resetLabel = stringResource(Res.string.song_details_tempo_reset),
                    onReset = viewModel::resetMetronomeTempo,
                    repeatsOnHold = true,
                    isVertical = isVertical,
                )
            } else {
                val description = stringResource(Res.string.song_details_tempo, pattern.bpm.toString())
                Text(
                    modifier = Modifier.padding(8.dp).semantics { contentDescription = description },
                    text = pattern.bpm.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (isVertical) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                    .width(PANEL_THICKNESS + PANEL_SIDE_EXTRA_WIDTH)
                    .padding(vertical = PANEL_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PANEL_PADDING),
            ) {
                playButton()
                tempo()
                beatRow(Modifier.padding(horizontal = PANEL_PADDING))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .height(PANEL_THICKNESS)
                    .padding(horizontal = PANEL_PADDING),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PANEL_PADDING),
            ) {
                playButton()
                beatRow(Modifier.weight(1f))
                tempo()
            }
        }
    }
}

/** The height of the panel as a bar; as a column it is a little wider, for a bar of many beats to still be told apart. */
private val PANEL_THICKNESS = 56.dp
private val PANEL_SIDE_EXTRA_WIDTH = 24.dp
private val PANEL_PADDING = 12.dp
private val PANEL_BEAT_HEIGHT = 24.dp
private val PANEL_BEAT_GAP = 3.dp
