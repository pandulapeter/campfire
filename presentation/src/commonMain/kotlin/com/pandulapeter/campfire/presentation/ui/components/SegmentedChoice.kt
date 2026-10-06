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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A single choice between a handful of [options], rendered as a segmented button row. A selected segment carries
 * Material's check mark only where every label of the row would still fit beside it ([hasRoomForCheckMarks]): a row
 * that has no room for one has none at all, its fill alone marking the choice, so that the name of the selected
 * option is never the one cut short and picking another never takes the mark away.
 *
 * Measuring the labels against the row's width makes a row that is not [isInline] a `BoxWithConstraints`, which is a
 * `SubcomposeLayout` and throws where a parent asks for its intrinsic size, so it is not to be put inside an
 * `IntrinsicSize` layout.
 *
 * @param isEnabled False where the choice has no effect right now, in which case the row still shows which option
 *   is selected rather than disappearing: what it would go back to once it matters again is worth seeing.
 * @param isInline True where the choice shares a row with other controls, as in the editor's bar: it keeps a
 *   narrower gap from them than from the edges of a screen, a selected segment has no check mark - its fill
 *   already marks it, and the mark's 26dp is most of what a label has there on a phone - and the labels are padded
 *   less at their sides ([INLINE_CONTENT_PADDING]).
 */
@Composable
internal fun <T> SegmentedChoice(
    modifier: Modifier = Modifier,
    options: List<Pair<T, String>>,
    selected: T?,
    shouldApplyPadding: Boolean = true,
    isEnabled: Boolean = true,
    isInline: Boolean = false,
    onSelected: (T) -> Unit,
) {
    val rowModifier = modifier.fillMaxWidth().padding(horizontal = if (shouldApplyPadding) if (isInline) 8.dp else 16.dp else 0.dp)
    if (isInline) {
        SegmentedRow(
            modifier = rowModifier,
            options = options,
            selected = selected,
            isEnabled = isEnabled,
            contentPadding = INLINE_CONTENT_PADDING,
            showsCheckMarks = false,
            onSelected = onSelected,
        )
    } else {
        BoxWithConstraints(rowModifier) {
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val style = MaterialTheme.typography.labelLarge
            val textMeasurer = rememberTextMeasurer()
            // Compared by equality, so the labels are measured again only when one of them changes (the language), or
            // when the density does, which carries the system's font scale.
            val labels = options.map { it.second }
            val labelWidths = remember(labels, style, density) {
                labels.map { textMeasurer.measure(text = it, style = style, maxLines = 1).size.width.toFloat() }
            }
            val contentPadding = SegmentedButtonDefaults.ContentPadding
            val showsCheckMarks = with(density) {
                hasRoomForCheckMarks(
                    labelWidths = labelWidths,
                    // Each segment's 1dp border is taken off the width its label shares.
                    rowWidth = maxWidth.toPx() - options.size * 1.dp.toPx(),
                    contentPadding = (contentPadding.calculateStartPadding(layoutDirection) + contentPadding.calculateEndPadding(layoutDirection)).toPx(),
                    checkMark = (SegmentedButtonDefaults.IconSize + CHECK_MARK_SPACING).toPx(),
                )
            }
            SegmentedRow(
                modifier = Modifier.fillMaxWidth(),
                options = options,
                selected = selected,
                isEnabled = isEnabled,
                contentPadding = contentPadding,
                showsCheckMarks = showsCheckMarks,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    modifier: Modifier,
    options: List<Pair<T, String>>,
    selected: T?,
    isEnabled: Boolean,
    contentPadding: PaddingValues,
    showsCheckMarks: Boolean,
    onSelected: (T) -> Unit,
) = SingleChoiceSegmentedButtonRow(modifier = modifier) {
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            selected = value == selected,
            onClick = { onSelected(value) },
            enabled = isEnabled,
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            icon = if (showsCheckMarks) ({ SegmentedButtonDefaults.Icon(value == selected) }) else ({}),
            contentPadding = contentPadding,
            label = {
                Text(
                    // Material measures the label at the whole width of the segment and then places it after the check
                    // mark of a selected one, so a label as wide as the segment ran on under its shape instead of being
                    // ellipsized. The check mark's room is taken off the width the label is offered.
                    modifier = if (value == selected && showsCheckMarks) Modifier.withoutCheckMarkWidth() else Modifier,
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

/**
 * Whether every label of a row of segments [rowWidth] wide still fits its segment with a selected segment's check
 * mark beside it, all in pixels. Decided for the whole row, so that picking another option never takes the mark away.
 */
internal fun hasRoomForCheckMarks(
    labelWidths: List<Float>,
    rowWidth: Float,
    contentPadding: Float,
    checkMark: Float,
): Boolean {
    if (labelWidths.isEmpty()) return true
    val labelRoom = rowWidth / labelWidths.size - contentPadding - checkMark
    return labelWidths.all { it <= labelRoom }
}

/**
 * Measures the content with the width of a selected segment's check mark and the gap after it taken off: 18dp
 * (`SegmentedButtonDefaults.IconSize`) and 8dp, which is Material's own spacing and not public.
 */
private fun Modifier.withoutCheckMarkWidth() = layout { measurable, constraints ->
    val reserved = (SegmentedButtonDefaults.IconSize + CHECK_MARK_SPACING).roundToPx()
    val placeable = measurable.measure(
        if (constraints.hasBoundedWidth) {
            constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth - reserved).coerceAtLeast(0))
        } else {
            constraints
        },
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private val CHECK_MARK_SPACING = 8.dp

/**
 * Inline, the segments share a row with other controls on a phone, and Material's 12dp at each side of the label is
 * what cut the editor's "Preview" short at 360dp. The vertical padding stays Material's, so the row keeps its height.
 */
private val INLINE_CONTENT_PADDING = PaddingValues(
    start = 6.dp,
    top = SegmentedButtonDefaults.ContentPadding.calculateTopPadding(),
    end = 6.dp,
    bottom = SegmentedButtonDefaults.ContentPadding.calculateBottomPadding(),
)
