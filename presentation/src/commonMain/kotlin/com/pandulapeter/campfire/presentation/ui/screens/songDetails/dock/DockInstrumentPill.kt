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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.isStandard
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel

/**
 * The instrument in the player's hands and its tuning, which the dock's diagrams are drawn for, at the start of the
 * dock's chords: one outlined tonal pill ([DockControlPill]) as the tempo chip is, so that the diagrams stand between the
 * player's two controls. Shown only where the diagrams (or a short window's chord chips) are, since it says what they are
 * drawn for; an instrument without shapes, which has none, is changed in the Playing sheet. Its two parts open their two
 * sheets ([ChordDiagrams.onInstrumentClicked], [ChordDiagrams.onTuningClicked]), as the instrument pill of a song's page
 * did, so that the tuning is one press away rather than at the end of a long list; read only mode's too, since the
 * instrument is this device's preference rather than the song's. A screen reader hears it as one control naming both,
 * the tuning's sheet a custom action of it.
 *
 * [isTall] beside the diagrams (their row, the side panel's head): [height] tall, the tempo chip's height, the instrument
 * over the tuning; else chip-high (a short window's one row), the tuning after a stepper's divider. An instrument with one
 * tuning shows its name alone. A new name crossfades and the pill resizes with it.
 */
@Composable
internal fun DockInstrumentPill(
    modifier: Modifier = Modifier,
    chordDiagrams: ChordDiagrams,
    height: Dp,
    isTall: Boolean,
) {
    val onInstrumentClicked = chordDiagrams.onInstrumentClicked ?: return
    val onInstrument = {
        println("Dock: instrument pill")
        onInstrumentClicked()
    }
    val onTuning = {
        println("Dock: instrument pill tuning")
        chordDiagrams.onTuningClicked?.invoke()
        Unit
    }
    val instrument = instrumentLabel(chordDiagrams.instrument)
    val tuning = chordDiagrams.instrumentTuning?.takeIf { chordDiagrams.instrument.tunings.size > 1 }?.let {
        if (it.isStandard) stringResource(Res.string.instrument_tuning_standard) else tuningLabel(it, chordDiagrams.notation)
    }
    val description = listOfNotNull(instrument, tuning?.let { stringResource(Res.string.song_details_chord_tuning, it) }).joinToString(", ")
    val instrumentAction = stringResource(Res.string.instrument_choose)
    val tuningAction = stringResource(Res.string.instrument_tuning_choose)
    DockControlPill(
        modifier = modifier.height(height),
        shape = if (isTall) RoundedCornerShape(DOCK_TALL_CORNER) else CircleShape,
        onClickLabel = instrumentAction,
        // Each part takes its own press.
        onClick = null,
    ) {
        Box(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = instrumentAction) {
                    onInstrument()
                    true
                }
                if (tuning != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(tuningAction) {
                            onTuning()
                            true
                        },
                    )
                }
            },
        ) {
            AnimatedContent(
                targetState = tuning != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.Center,
            ) { hasTuning ->
                when {
                    isTall -> TallParts(instrument = instrument, tuning = tuning?.takeIf { hasTuning }, onInstrument = onInstrument, onTuning = onTuning)
                    else -> ChipParts(instrument = instrument, tuning = tuning?.takeIf { hasTuning }, onInstrument = onInstrument, onTuning = onTuning)
                }
            }
        }
    }
}

/**
 * The tall pill's parts: the instrument over its [tuning] (null for an instrument with one), each its half's press. As
 * wide as the wider name, or as the pill is made (the side panel's), the two halves as wide as each other.
 */
@Composable
private fun TallParts(
    instrument: String,
    tuning: String?,
    onInstrument: () -> Unit,
    onTuning: () -> Unit,
) = Column(modifier = Modifier.fillMaxHeight().width(IntrinsicSize.Max)) {
    Part(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PILL_PADDING),
        text = instrument,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        // The two names meet in the middle, so that they read as one label rather than two halves apart.
        alignment = if (tuning == null) Alignment.Center else Alignment.BottomCenter,
        onClick = onInstrument,
    )
    if (tuning != null) {
        Part(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PILL_PADDING),
            text = tuning,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            alignment = Alignment.TopCenter,
            onClick = onTuning,
        )
    }
}

/** The chip-high pill's parts: the instrument, then its [tuning] (null for an instrument with one) after a divider. */
@Composable
private fun ChipParts(
    instrument: String,
    tuning: String?,
    onInstrument: () -> Unit,
    onTuning: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxHeight(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Part(
        modifier = Modifier.fillMaxHeight().padding(start = PILL_PADDING, end = if (tuning == null) PILL_PADDING else PART_PADDING),
        text = instrument,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        alignment = Alignment.Center,
        onClick = onInstrument,
    )
    if (tuning != null) {
        StepperDivider()
        Part(
            modifier = Modifier.fillMaxHeight().padding(start = PART_PADDING, end = PILL_PADDING),
            text = tuning,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            alignment = Alignment.Center,
            onClick = onTuning,
        )
    }
}

/**
 * One part of the pill, its name crossfading as it changes. The press is taken before the padding, so that its ripple
 * reaches the pill's edges; [modifier] carries the padding after it.
 */
@Composable
private fun Part(
    modifier: Modifier,
    text: String,
    style: TextStyle,
    color: Color,
    alignment: Alignment,
    onClick: () -> Unit,
) = Box(
    modifier = Modifier.clickable(onClick = onClick).then(modifier),
    contentAlignment = alignment,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = alignment,
    ) { shownText ->
        Text(
            text = shownText,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PILL_PADDING = 12.dp
private val PART_PADDING = 8.dp
