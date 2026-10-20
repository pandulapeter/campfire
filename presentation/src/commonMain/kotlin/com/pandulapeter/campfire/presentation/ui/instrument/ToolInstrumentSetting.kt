/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_END_INSET
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument at the top of a tool's page (the Chords, the Scales, and the Tuner tabs), the same on all three: the
 * [InstrumentPill] at the start of a line of the list screens' bar height, with [trailing] at its end where their pill and
 * New button stand (the Tuner's reference pitch). The pill gives up width before it reaches [trailing], cutting its
 * tuning short, and where it could no longer show the tuning beside them it keeps the line to itself and [trailing]
 * slides under it, still at the end, the line growing with it (`pillSharesLine`, `toolLinePlaces`). A page with nothing
 * at the end keeps the room above the pill all the same, so that the pill stands at one spot whichever tool is picked,
 * but not the room under it, which only parted it from what comes next by more than the page's other groups are parted.
 */
@Composable
internal fun ToolInstrumentSetting(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    isEnabled: Boolean = true,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val pill = @Composable { pillModifier: Modifier ->
        InstrumentPill(
            modifier = pillModifier,
            instrument = instrument,
            tuning = tuning,
            notation = notation,
            isEnabled = isEnabled,
            onInstrumentClicked = onInstrumentClicked,
            onTuningClicked = onTuningClicked,
        )
    }
    if (trailing == null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = (LIST_APP_BAR_HEIGHT - songControlHeight(MaterialTheme.typography.titleSmall)) / 2)
                .padding(start = 16.dp, end = NEW_ITEM_BUTTON_END_INSET),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            pill(Modifier.weight(1f, fill = false).padding(end = NEW_ITEM_BUTTON_GAP))
        }
    } else {
        ToolInstrumentLine(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = NEW_ITEM_BUTTON_END_INSET),
            hasTuning = shownTuning(instrument, tuning) != null,
            pill = { pill(Modifier) },
            trailing = trailing,
        )
    }
}

/**
 * The line of a tool's instrument with [trailing] controls: beside the [pill] while it can still show its tuning there,
 * under it otherwise, sliding between the two (see [ToolLineMeasurePolicy]).
 */
@Composable
private fun ToolInstrumentLine(
    modifier: Modifier,
    hasTuning: Boolean,
    pill: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val motion = remember { ToolLineMotion() }
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val textStyle = MaterialTheme.typography.titleSmall
    val pillHeight = songControlHeight(textStyle)
    val measurePolicy = remember(motion, scope, spec, hasTuning, pillHeight, textStyle.fontSize) {
        ToolLineMeasurePolicy(
            motion = motion,
            scope = scope,
            spec = spec,
            hasTuning = hasTuning,
            pillHeight = pillHeight,
            fontSize = textStyle.fontSize,
        )
    }
    Layout(
        modifier = modifier,
        content = {
            pill()
            trailing()
        },
        measurePolicy = measurePolicy,
    )
}

/** How far a tool's instrument line is on its way to the pill's own line. Not a state: only the [Animatable] is read. */
private class ToolLineMotion(var progress: Animatable<Float, AnimationVector1D>? = null)

/**
 * Lays out the pill and the trailing controls of a [ToolInstrumentLine]. Whether they share the line depends on width
 * alone (the pill's uncut width, the controls' measured against the whole line in both arrangements), never on the
 * progress or the height, so a change settles in one measure and the keyboard never sends the controls back up while
 * the player types into them. Nothing is written while measuring but the slide launched when the decision changes; the
 * [Animatable] is created at the first measure's target, so opening the tab slides nothing. The intrinsics are answered
 * from the children's own as the line beside the pill, since the default ones would run the measure at an unbounded
 * width, creating the [Animatable] at that pass's target and laying out at an infinite width.
 */
private class ToolLineMeasurePolicy(
    private val motion: ToolLineMotion,
    private val scope: CoroutineScope,
    private val spec: FiniteAnimationSpec<Float>,
    private val hasTuning: Boolean,
    private val pillHeight: Dp,
    private val fontSize: TextUnit,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val lineHeight = LIST_APP_BAR_HEIGHT.roundToPx()
        val gap = NEW_ITEM_BUTTON_GAP.roundToPx()
        val lineConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = lineHeight)
        val trailing = measurables[1].measure(lineConstraints)
        if (!constraints.hasBoundedWidth) {
            val pill = measurables[0].measure(lineConstraints)
            val height = constraints.constrainHeight(lineHeight)
            return layout(pill.width + gap + trailing.width, height) {
                pill.placeRelative(0, (height - pill.height) / 2)
                trailing.placeRelative(pill.width + gap, (height - trailing.height) / 2)
            }
        }
        val lineWidth = constraints.maxWidth
        val sharesLine = pillSharesLine(
            lineWidth = lineWidth,
            trailingWidth = trailing.width,
            gap = gap,
            pillWidth = measurables[0].maxIntrinsicWidth(lineHeight),
            hasTuning = hasTuning,
            nameRoom = (if (hasTuning) INSTRUMENT_PILL_TUNING_ROOM else INSTRUMENT_PILL_LONE_ROOM).roundToPx(),
            minNameWidth = (fontSize.toPx() * MIN_SHOWN_NAME_EMS).roundToInt(),
        )
        val target = if (sharesLine) 0f else 1f
        // The lookahead lays the line out where it is headed, as PlacementAnimation does.
        val progress = if (isLookingAhead) target else progressTowards(target)
        val places = toolLinePlaces(
            progress = progress,
            lineWidth = lineWidth,
            lineHeight = lineHeight,
            gap = gap,
            pillHeight = pillHeight.roundToPx(),
            trailingWidth = trailing.width,
            trailingHeight = trailing.height,
        )
        val pill = measurables[0].measure(Constraints(maxWidth = places.pillMaxWidth, maxHeight = lineHeight))
        return layout(lineWidth, constraints.constrainHeight(places.height)) {
            pill.placeRelative(0, (lineHeight - pill.height) / 2)
            trailing.placeRelative(lineWidth - trailing.width, places.trailingY)
        }
    }

    private fun progressTowards(target: Float): Float {
        val animatable = motion.progress ?: Animatable(target).also { motion.progress = it }
        if (animatable.targetValue != target) {
            scope.launch { animatable.animateTo(target, spec) }
        }
        return animatable.value
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val lineHeight = LIST_APP_BAR_HEIGHT.roundToPx()
        return measurables[0].maxIntrinsicWidth(lineHeight) + NEW_ITEM_BUTTON_GAP.roundToPx() + measurables[1].maxIntrinsicWidth(lineHeight)
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables[1].minIntrinsicWidth(LIST_APP_BAR_HEIGHT.roundToPx()) + NEW_ITEM_BUTTON_GAP.roundToPx()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        maxOf(LIST_APP_BAR_HEIGHT.roundToPx(), measurables[1].maxIntrinsicHeight(width))

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        maxOf(LIST_APP_BAR_HEIGHT.roundToPx(), measurables[1].minIntrinsicHeight(width))
}
