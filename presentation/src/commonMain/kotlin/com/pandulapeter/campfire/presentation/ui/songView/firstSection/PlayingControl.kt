/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.scaled

/**
 * One of [SongPlayingControlsRow]'s items: what it is and what sets it, and the tool buttons that end its group where
 * it is the last of it ([trailing]). The label is in the content color, since the controls themselves already set the
 * row apart.
 *
 * The label stands before the control while the two fit side by side, and above it where they do not: at a large text
 * size on a phone, a long label (Hungarian's "Transzponálás") and a stepper grown with the text are wider than the
 * screen together, and a row would hand the control only what the label left, cutting its value and its Tap segment
 * off. Where [isAnimated], the control springs between the two places, as the items of [GroupedRows] do.
 */
@Composable
internal fun PlayingControl(
    modifier: Modifier = Modifier,
    label: String,
    fontScale: Float,
    isAnimated: Boolean,
    trailing: @Composable () -> Unit,
    control: @Composable RowScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val motion = remember(coroutineScope) { PlacementMotion(coroutineScope) }
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                control()
                trailing()
            }
        },
        measurePolicy = PlayingControlMeasurePolicy(motion = motion, isAnimated = isAnimated),
    )
}

/** Measures [PlayingControl]'s two children, the label first and the control second. */
private class PlayingControlMeasurePolicy(
    private val motion: PlacementMotion,
    private val isAnimated: Boolean,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val (labelMeasurable, controlMeasurable) = measurables
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val label = labelMeasurable.measure(loose)
        val control = controlMeasurable.measure(loose)
        val besideGap = PLAYING_CONTROL_LABEL_GAP.roundToPx()
        val isBeside = !constraints.hasBoundedWidth || label.width + besideGap + control.width <= constraints.maxWidth
        val width: Int
        val height: Int
        val labelTarget: IntOffset
        val controlTarget: IntOffset
        if (isBeside) {
            width = label.width + besideGap + control.width
            height = maxOf(label.height, control.height)
            labelTarget = IntOffset(0, (height - label.height) / 2)
            controlTarget = IntOffset(label.width + besideGap, (height - control.height) / 2)
        } else {
            val stackedGap = PLAYING_CONTROL_STACKED_GAP.roundToPx()
            width = maxOf(label.width, control.width)
            height = label.height + stackedGap + control.height
            labelTarget = IntOffset.Zero
            controlTarget = IntOffset(0, label.height + stackedGap)
        }
        // The lookahead pass of the song's sections is where they are headed, so the control is placed there at once.
        val animatedMotion = motion.takeIf { isAnimated && !isLookingAhead }
        if (!isAnimated) motion.clear()
        return layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), maxOf(height, constraints.minHeight)) {
            if (animatedMotion == null) {
                label.placeRelative(labelTarget)
                control.placeRelative(controlTarget)
            } else with(animatedMotion) {
                place(placeable = label, key = LABEL_KEY, target = labelTarget)
                place(placeable = control, key = CONTROL_KEY, target = controlTarget)
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOf { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.sumOf { it.maxIntrinsicWidth(height) } + PLAYING_CONTROL_LABEL_GAP.roundToPx()

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        intrinsicHeight(measurables, width) { measurable, itemWidth -> measurable.minIntrinsicHeight(itemWidth) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        intrinsicHeight(measurables, width) { measurable, itemWidth -> measurable.maxIntrinsicHeight(itemWidth) }

    /** The height [measure] arrives at for [width]: the taller of the two side by side, or both and the gap stacked. */
    private fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        heightOf: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        val (label, control) = measurables
        val labelWidth = label.maxIntrinsicWidth(Constraints.Infinity)
        val controlWidth = control.maxIntrinsicWidth(Constraints.Infinity)
        return if (labelWidth + PLAYING_CONTROL_LABEL_GAP.roundToPx() + controlWidth <= width) {
            maxOf(heightOf(label, labelWidth), heightOf(control, controlWidth))
        } else {
            heightOf(label, minOf(labelWidth, width)) + PLAYING_CONTROL_STACKED_GAP.roundToPx() + heightOf(control, minOf(controlWidth, width))
        }
    }
}

private const val LABEL_KEY = "label"
private const val CONTROL_KEY = "control"
private val PLAYING_CONTROL_LABEL_GAP = 8.dp

/** Between a label and the control under it, closer than beside it, since the label's line height already parts them. */
private val PLAYING_CONTROL_STACKED_GAP = 4.dp
