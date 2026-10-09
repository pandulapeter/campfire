/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * A setting's label with its controls at the end of the row, the way a switch row has its switch, for as long as the
 * label's longest word still fits beside them; under that, the label goes above the controls, the way
 * [SettingsSubsection] sets a control that does not fit at the end of a row. The controls do not wrap and grow with the
 * text size, and a label handed only what they leave would be broken in the middle of a word.
 *
 * The change between the two forms comes from the text size or the window rather than from something the user did, so
 * it is not animated. Nor may it come from the value being set: a row whose label or controls change width with the
 * value would otherwise flip while a slider under it is dragged or a button of it is held, moving both under the finger.
 * So a row that stacked at a width stays stacked at that width, and only a new width decides afresh.
 *
 * @param decidingLabel Measured for the decision alone and never drawn: the widest the label can become, for a label
 * that changes with the value (the tempo's marking), so that the form follows the widest one whatever is shown.
 */
@Composable
internal fun LabeledControlRow(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    decidingLabel: (@Composable () -> Unit)? = null,
    controls: @Composable () -> Unit,
) {
    val stackedWidth = remember { StackedWidth() }
    Layout(
        modifier = modifier,
        contents = listOf<@Composable () -> Unit>(
            label,
            controls,
            { decidingLabel?.let { Box(modifier = Modifier.clearAndSetSemantics {}) { it() } } },
        ),
    ) { (labelMeasurables, controlsMeasurables, decidingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val controls = controlsMeasurables.map { it.measure(loose) }
        val controlsWidth = controls.maxOfOrNull { it.width } ?: 0
        val controlsHeight = controls.maxOfOrNull { it.height } ?: 0
        val gap = LABEL_CONTROLS_GAP.roundToPx()
        val labelMinWidth = (labelMeasurables + decidingMeasurables).maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0
        if (!constraints.hasBoundedWidth) {
            val labels = labelMeasurables.map { it.measure(Constraints(minWidth = labelMinWidth, maxWidth = labelMinWidth)) }
            val height = maxOf(labels.maxOfOrNull { it.height } ?: 0, controlsHeight, constraints.minHeight)
            val width = labelMinWidth + gap + controlsWidth
            layout(width, height) {
                labels.forEach { it.placeRelative(0, (height - it.height) / 2) }
                controls.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
            }
        } else {
            val maxWidth = constraints.maxWidth
            if (isLabelBesideControls(maxWidth, labelMinWidth, controlsWidth, gap, stackedAtWidth = stackedWidth.width)) {
                val labelWidth = (maxWidth - controlsWidth - gap).coerceAtLeast(0)
                val labels = labelMeasurables.map { it.measure(Constraints(minWidth = labelWidth, maxWidth = labelWidth)) }
                val height = maxOf(labels.maxOfOrNull { it.height } ?: 0, controlsHeight, constraints.minHeight)
                layout(maxWidth, height) {
                    labels.forEach { it.placeRelative(0, (height - it.height) / 2) }
                    controls.forEach { it.placeRelative(maxWidth - it.width, (height - it.height) / 2) }
                }
            } else {
                stackedWidth.width = maxWidth
                val labels = labelMeasurables.map { it.measure(loose) }
                val labelHeight = labels.maxOfOrNull { it.height } ?: 0
                val stackedGap = SUBSECTION_CONTROL_GAP.roundToPx()
                val height = maxOf(labelHeight + stackedGap + controlsHeight, constraints.minHeight)
                layout(maxWidth, height) {
                    labels.forEach { it.placeRelative(0, 0) }
                    controls.forEach { it.placeRelative(0, labelHeight + stackedGap) }
                }
            }
        }
    }
}

/**
 * Whether a label whose longest word is [labelMinWidth] wide still fits beside controls [controlsWidth] wide, [gap]
 * apart, in a row [maxWidth] wide, all in pixels. A row that stacked at [stackedAtWidth] stays stacked at that width
 * (-1 for one that never stacked), whatever the controls have narrowed to since.
 */
internal fun isLabelBesideControls(
    maxWidth: Int,
    labelMinWidth: Int,
    controlsWidth: Int,
    gap: Int,
    stackedAtWidth: Int,
) = maxWidth != stackedAtWidth && labelMinWidth + gap + controlsWidth <= maxWidth

/** The width a [LabeledControlRow] last stacked at. Not a state: it is written and read during measurement only. */
private class StackedWidth(var width: Int = -1)

private val LABEL_CONTROLS_GAP = 16.dp
