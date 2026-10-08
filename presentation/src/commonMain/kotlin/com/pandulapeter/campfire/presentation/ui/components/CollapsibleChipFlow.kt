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

import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * A group of filter chips flowed onto as many lines as the height it is given holds, with [toggle] under them once
 * they do not all fit: the lines are counted from the room [FilterGroupsLayout] hands out rather than from a fixed
 * number of chips, so a wide panel shows a whole library of short tags and a phone shows fewer long ones.
 *
 * Collapsed, the chips shown are the first ones in their order and every one [isPinned] marks: a selected value has
 * to be visible to be turned off, wherever it is in the order. The rest are composed but not placed, which keeps them
 * off the screen, out of the semantics and out of the focus order.
 *
 * Its intrinsic heights are what the sharing is decided by, and they are the collapsed group's whatever [isExpanded]
 * says: the max is every chip, the min [MIN_COLLAPSED_LINES] lines, the pinned chips and the toggle.
 *
 * A chip that changes places - the group sorted the other way, or a count that moved it past another - travels there
 * (`animateBounds`) rather than every chip staying put and being handed another label, which leaves the eye nothing to
 * follow. That is why the chips are composed here, each under its [key]: composed in a plain loop, the chip in the first
 * place would stay the first chip whatever value it showed.
 *
 * Opening and closing the group is animated both ways in [lookaheadScope], the filter column's one scope: the lookahead
 * pass decides which chips are shown, and the approach pass animates the group's bounds towards that while a chip that
 * joins or leaves fades as the group's edge passes it ([chipTransitionAlpha]) and the toggle rides that edge. The
 * group's first layout, and one after a transition has ended, narrate nothing.
 *
 * @param isExpanded Every chip and the toggle, however much room there is: the user asked to see them all.
 * @param isPinned One value per item, in their order.
 * @param horizontalPadding Between the chips and the edges; the toggle is placed at the start edge and pads itself.
 */
@Composable
internal fun <T : Any> CollapsibleChipFlow(
    modifier: Modifier = Modifier,
    items: List<T>,
    key: (T) -> Any,
    isExpanded: Boolean,
    isPinned: List<Boolean>,
    lookaheadScope: LookaheadScope,
    horizontalPadding: Dp,
    gap: Dp,
    toggle: @Composable () -> Unit,
    chip: @Composable (T) -> Unit,
) {
    // Outside the policy, which is made again whenever the group is opened or closed: the target it decided last is
    // what the next one animates from.
    val target = remember { ChipFlowTarget() }
    val measurePolicy = remember(isExpanded, isPinned, horizontalPadding, gap) {
        CollapsibleChipFlowMeasurePolicy(
            isExpanded = isExpanded,
            isPinned = isPinned,
            horizontalPadding = horizontalPadding,
            gap = gap,
            target = target,
        )
    }
    Layout(
        // The same motion the chips travel by, and no clip: animateContentSize clipped the rows to its animated size,
        // so they were uncovered by a hard edge, and a group closing lost its height to the room it was given at once.
        modifier = modifier.animateBounds(lookaheadScope),
        contents = listOf(
            {
                items.forEach { item ->
                    key(key(item)) {
                        Box(modifier = Modifier.animateBounds(lookaheadScope)) { chip(item) }
                    }
                }
            },
            toggle,
        ),
        measurePolicy = measurePolicy,
    )
}

/**
 * What the lookahead pass of a [CollapsibleChipFlow] decided, for the approach pass to animate towards, and what was
 * shown before it, for the chips that leave to fade out where they were. Positions are keyed by the chip's index; the
 * chips' keys keep their nodes. No previous state ([previousShown] null) is the group at rest, or laid out for the
 * first time, which is drawn with no layers and no fades.
 */
private class ChipFlowTarget {
    var isFilled = false
    var positions: Map<Int, IntOffset> = emptyMap()
    var isToggleShown = false
    var height = 0
    var previousShown: Map<Int, IntOffset>? = null
    var previousToggleShown = false
    var previousHeight = 0
}

private class CollapsibleChipFlowMeasurePolicy(
    private val isExpanded: Boolean,
    private val isPinned: List<Boolean>,
    private val horizontalPadding: Dp,
    private val gap: Dp,
    private val target: ChipFlowTarget,
) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val (chipMeasurables, toggleMeasurables) = measurables
        val padding = horizontalPadding.roundToPx()
        val gap = gap.roundToPx()
        val width = constraints.maxWidth
        val lineWidth = (width - 2 * padding).coerceAtLeast(0)
        val chips = chipMeasurables.map { it.measure(Constraints(maxWidth = lineWidth)) }
        val toggle = toggleMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        if (!isLookingAhead) return approach(width, constraints, chips, toggle)
        val widths = chips.map { it.width }
        val lineHeight = chips.maxOfOrNull { it.height } ?: 0
        val toggleHeight = toggle?.height ?: 0
        fun heightOf(lineCount: Int) = flowHeight(lineCount = lineCount, lineHeight = lineHeight, gap = gap)
        val shown = when {
            isExpanded -> chips.indices.toList()
            heightOf(chipLineCount(widths, lineWidth, gap)) <= constraints.maxHeight -> chips.indices.toList()
            else -> collapsedChips(
                widths = widths,
                isPinned = isPinned,
                lineWidth = lineWidth,
                gap = gap,
                maxLines = ((constraints.maxHeight - toggleHeight) / (lineHeight + gap).coerceAtLeast(1)).coerceAtLeast(1),
            )
        }
        val isToggleShown = isExpanded || shown.size < chips.size
        val lines = chipLines(shown.map { widths[it] }, lineWidth, gap)
        val chipsHeight = heightOf(lines.lastOrNull()?.plus(1) ?: 0)
        val height = constraints.constrainHeight(chipsHeight + if (isToggleShown) toggleHeight else 0)
        val positions = LinkedHashMap<Int, IntOffset>()
        var line = -1
        var x = 0
        shown.forEachIndexed { position, index ->
            if (lines[position] != line) {
                line = lines[position]
                x = 0
            }
            positions[index] = IntOffset(padding + x, line * (lineHeight + gap))
            x += chips[index].width + gap
        }
        // A change of what is shown starts a transition from the last target; one that reverses another midway starts
        // from where that one was going, which is close enough to where it was.
        if (target.isFilled && (positions.keys != target.positions.keys || isToggleShown != target.isToggleShown)) {
            target.previousShown = target.positions
            target.previousToggleShown = target.isToggleShown
            target.previousHeight = target.height
        }
        target.isFilled = true
        target.positions = positions
        target.isToggleShown = isToggleShown
        target.height = height
        val leaving = target.previousShown.orEmpty().filterKeys { it !in positions && it < chips.size }
        return layout(width, height) {
            positions.forEach { (index, offset) -> chips[index].placeRelative(offset) }
            // Nothing placed here is drawn, but each chip's animateBounds reads its lookahead placement while the
            // approach pass places it: a leaving chip left out here would be read at a stale position.
            leaving.forEach { (index, offset) -> chips[index].placeRelative(offset) }
            if (isToggleShown || target.previousShown != null && target.previousToggleShown) {
                toggle?.placeRelative(0, chipsHeight)
            }
        }
    }

    /**
     * The approach pass: the group at the height its animateBounds has come to, with the chips the lookahead pass
     * decided on at their places, the ones it took away fading where they were, and the toggle at the edge.
     */
    private fun MeasureScope.approach(
        width: Int,
        constraints: Constraints,
        chips: List<Placeable>,
        toggle: Placeable?,
    ): MeasureResult {
        val height = constraints.constrainHeight(target.height)
        val previousShown = target.previousShown
        val toggleHeight = toggle?.height ?: 0
        if (previousShown == null || height == target.height) {
            // At rest, or the transition has come to its end: the chips that left are no longer placed, which takes them
            // out of the semantics as well.
            target.previousShown = null
            return layout(width, height) {
                target.positions.forEach { (index, offset) -> chips.getOrNull(index)?.placeRelative(offset) }
                if (target.isToggleShown) toggle?.placeRelative(0, height - toggleHeight)
            }
        }
        val isToggleInvolved = target.isToggleShown || target.previousToggleShown
        val edge = if (isToggleInvolved) height - toggleHeight else height
        val progress = if (target.height == target.previousHeight) {
            1f
        } else {
            ((height - target.previousHeight).toFloat() / (target.height - target.previousHeight)).coerceIn(0f, 1f)
        }
        return layout(width, height) {
            target.positions.forEach { (index, offset) ->
                val chip = chips.getOrNull(index) ?: return@forEach
                if (index in previousShown) {
                    chip.placeRelative(offset)
                } else {
                    chip.placeRelativeWithLayer(offset) {
                        alpha = chipTransitionAlpha(offset.y, chip.height, edge, progress, isEntering = true)
                    }
                }
            }
            previousShown.forEach { (index, offset) ->
                if (index in target.positions) return@forEach
                val chip = chips.getOrNull(index) ?: return@forEach
                chip.placeRelativeWithLayer(offset) {
                    alpha = chipTransitionAlpha(offset.y, chip.height, edge, progress, isEntering = false)
                }
            }
            if (isToggleInvolved && toggle != null) {
                val toggleAlpha = when {
                    target.isToggleShown && target.previousToggleShown -> 1f
                    target.isToggleShown -> progress
                    else -> 1f - progress
                }
                toggle.placeRelativeWithLayer(0, height - toggleHeight) { alpha = toggleAlpha }
            }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int {
        val (chips, _) = measurables
        val gap = gap.roundToPx()
        val lineWidth = (width - 2 * horizontalPadding.roundToPx()).coerceAtLeast(0)
        val widths = chips.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(lineWidth) }
        return flowHeight(
            lineCount = chipLineCount(widths, lineWidth, gap),
            lineHeight = chips.maxOfOrNull { it.maxIntrinsicHeight(lineWidth) } ?: 0,
            gap = gap,
        )
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int {
        val (chips, toggle) = measurables
        val gap = gap.roundToPx()
        val lineWidth = (width - 2 * horizontalPadding.roundToPx()).coerceAtLeast(0)
        val widths = chips.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(lineWidth) }
        val lineHeight = chips.maxOfOrNull { it.maxIntrinsicHeight(lineWidth) } ?: 0
        val lineCount = chipLineCount(widths, lineWidth, gap)
        val pinnedLineCount = chipLineCount(widths.filterIndexed { index, _ -> isPinned.getOrElse(index) { false } }, lineWidth, gap)
        val collapsedLineCount = maxOf(MIN_COLLAPSED_LINES, pinnedLineCount)
        val natural = flowHeight(lineCount = lineCount, lineHeight = lineHeight, gap = gap)
        return if (lineCount <= collapsedLineCount) {
            natural
        } else {
            val collapsed = flowHeight(lineCount = collapsedLineCount, lineHeight = lineHeight, gap = gap) +
                (toggle.firstOrNull()?.maxIntrinsicHeight(width) ?: 0)
            minOf(collapsed, natural)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        2 * horizontalPadding.roundToPx() + (measurables.first().maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val chips = measurables.first()
        return 2 * horizontalPadding.roundToPx() + chips.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } +
            (chips.size - 1).coerceAtLeast(0) * gap.roundToPx()
    }
}

/**
 * How opaque a chip that joins or leaves the group is while its height animates: a row fades in as the group's edge
 * passes it, never ahead of the animation, so a chip joining a line already in sight (the rest of the last collapsed
 * line) fades in over the animation rather than appearing at once. Leaving is the same in reverse.
 */
internal fun chipTransitionAlpha(chipTop: Int, chipHeight: Int, edge: Int, progress: Float, isEntering: Boolean): Float {
    val sweep = ((edge - chipTop).toFloat() / chipHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
    return minOf(sweep, if (isEntering) progress else 1f - progress)
}

/**
 * The height of [lineCount] lines of chips, with a [gap] between the lines and one more under the last: the room
 * between the chips and whatever comes after them is the same as between two of their lines.
 */
private fun flowHeight(lineCount: Int, lineHeight: Int, gap: Int) =
    if (lineCount == 0) 0 else lineCount * lineHeight + lineCount * gap

/**
 * The line each chip of the given [widths] is laid out on when they are flowed into lines of [lineWidth], the way
 * `FlowRow` does it: a chip goes on the current line if it fits there after a [gap], and starts the next one if it
 * does not. A chip wider than a whole line still gets one of its own.
 */
internal fun chipLines(widths: List<Int>, lineWidth: Int, gap: Int): List<Int> {
    var line = -1
    var used = 0
    return widths.map { width ->
        if (line < 0 || used + gap + width > lineWidth) {
            line++
            used = width
        } else {
            used += gap + width
        }
        line
    }
}

/** How many lines [chipLines] lays the given [widths] out on. */
internal fun chipLineCount(widths: List<Int>, lineWidth: Int, gap: Int) = chipLines(widths, lineWidth, gap).lastOrNull()?.plus(1) ?: 0

/**
 * The chips a collapsed group shows on at most [maxLines] lines: the longest run from the start of the order that
 * fits there together with every pinned chip, in their order. The pinned ones are shown even where they alone take
 * more lines than that, since a selected filter has to be visible to be turned off; [CollapsibleChipFlow] reports
 * enough height for them as its minimum, so the room it is given holds them.
 */
internal fun collapsedChips(
    widths: List<Int>,
    isPinned: List<Boolean>,
    lineWidth: Int,
    gap: Int,
    maxLines: Int,
): List<Int> {
    fun shown(prefix: Int) = widths.indices.filter { it < prefix || isPinned.getOrElse(it) { false } }
    // Adding a chip to a flow never takes a line away from it, so the first run that does not fit ends the search.
    var prefix = 0
    while (prefix < widths.size && chipLineCount(shown(prefix + 1).map { widths[it] }, lineWidth, gap) <= maxLines) {
        prefix++
    }
    return shown(prefix)
}

/** The fewest lines a collapsed group is cut down to, however little room there is: one line reads as a stray row. */
private const val MIN_COLLAPSED_LINES = 2
