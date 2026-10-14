/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The text itself. Monospaced, because a tab or a grid only lines up in one and because a ChordPro document is
 * source rather than prose.
 *
 * The text size preference deliberately does not reach here: it is how large the lyrics are read from across a
 * room, and this is the source of the file rather than the song. Scaling it would also move the columns of a tab
 * away from the width the monospaced font is keeping them at.
 *
 * Lines are never wrapped: the staff lines of a tab only stay in their columns while each of them is one line on
 * screen, and a source file is read by its lines, which a wrapped one shows as two. A line wider than the pane is
 * scrolled to sideways instead.
 *
 * @param fieldModifier Applied innermost, right over the field's own focus target: the sideways scrolling container
 * around it is a focus group, so a focus callback or requester outside it would see that group rather than the field.
 * @param scrollState How far the field is scrolled down, hoisted so that it survives the pane being composed again.
 * @param horizontalScrollState How far the field is scrolled sideways, hoisted for the same reason.
 * @param searchHighlights The matches of the editor's search while its sheet is open, drawn behind the text; the current
 *   one is scrolled into view whenever it changes, since the field has no focus under the sheet to follow it with.
 * @param searchSheetTop Where the search sheet's top edge is on the screen ([EditorSearch.sheetTop]), above which the
 *   current match is revealed, or null while there is no sheet laid out.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun ChordProTextField(
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    isCompactTyping: Boolean,
    scrollState: ScrollState,
    horizontalScrollState: ScrollState,
    contentPadding: PaddingValues,
    searchHighlights: () -> SearchHighlights?,
    searchSheetTop: () -> Float?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val secondAccentColor = LocalSecondAccentColor.current
    val tokenCache = remember { ChordProTokenCache() }
    val outputTransformation = remember(colorScheme, secondAccentColor, tokenCache, searchHighlights) {
        ChordProOutputTransformation.of(
            tokenCache = tokenCache,
            primaryColor = colorScheme.primary,
            chordColor = secondAccentColor,
            secondaryColor = colorScheme.onSurfaceVariant,
            outlineColor = colorScheme.outline,
            errorColor = colorScheme.error,
            searchMatchColor = colorScheme.tertiaryContainer,
            currentSearchMatchColor = colorScheme.tertiary,
            onCurrentSearchMatchColor = colorScheme.onTertiary,
            searchHighlights = searchHighlights,
        )
    }
    val bodyLarge = MaterialTheme.typography.bodyLarge
    // The room after the last line is inside the scroll, as at the end of every list, so the text runs under the
    // navigation bar and comes clear of it once scrolled to the end. It is part of the field (its decorator), so a
    // press there still places the caret, and it never changes while the field is scrolled: a viewport resized at the
    // end of a drag sends the scroll back to the caret and interrupts the fling. The search sheet's room changes it only
    // as the sheet settles, under the sheet's scrim, which no finger scrolls the field through.
    val restingBottomInset = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding()
    val layoutDirection = LocalLayoutDirection.current
    val textLayout = remember { TextLayoutHolder() }
    val density = LocalDensity.current
    val textStart by rememberUpdatedState(with(density) { (contentPadding.calculateStartPadding(layoutDirection) + 16.dp).toPx() })
    // Snapshot state, read only by the reveal below: the field moves on its own while the sheet is up (the Shortcuts
    // collapsing as the keyboard comes, the title row of a short window), which has to reveal again like a moving sheet.
    var viewportTop by remember { mutableFloatStateOf(0f) }
    // The part of the pane the sheet covers, added after the last line while it is open, so that a match in the last
    // lines can still be scrolled up above it. It only changes once the sheet has settled, never while the field moves.
    var coveredRoom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(searchHighlights, searchSheetTop, scrollState, horizontalScrollState) {
        val margin = with(density) { REVEAL_MARGIN.toPx() }
        val currentMatch = snapshotFlow { searchHighlights()?.let { it.matchStarts.getOrNull(it.currentIndex) } }
        // Settled before it counts, so that the sheet sliding in and the keyboard animating do not start a scroll on
        // every frame. A new match is revealed at once.
        val uncoveredHeight = snapshotFlow {
            searchSheetTop()?.let { (it - viewportTop).coerceIn(0f, scrollState.viewportSize.toFloat()) }
        }.debounce(UNCOVERED_HEIGHT_SETTLING_MILLIS)
        combine(currentMatch, uncoveredHeight, ::Pair).collectLatest { (offset, height) ->
            val room = height?.let { scrollState.viewportSize - it } ?: 0f
            if (room > coveredRoom) {
                // Grown before the reveal: a scroll is clamped to the maximum of each of its frames, and one started
                // before the room is laid out stops short of it.
                coveredRoom = room
                awaitLayout()
            } else if (room < coveredRoom) {
                // Shrunk only once the text is above the shorter end, or it would jump down in one frame as the sheet
                // closes.
                val newMax = (scrollState.maxValue - (coveredRoom - room)).roundToInt().coerceAtLeast(0)
                if (scrollState.value > newMax) scrollState.animateScrollTo(newMax)
                coveredRoom = room
            }
            if (offset == null || height == null) return@collectLatest
            val layout = textLayout.getResult() ?: return@collectLatest
            revealOffset(
                offset = offset,
                layout = layout,
                scrollState = scrollState,
                horizontalScrollState = horizontalScrollState,
                textStart = textStart,
                uncoveredHeight = height,
                margin = margin,
            )
        }
    }
    BasicTextField(
        modifier = modifier
            // The keyboard reaches the field only through the content padding this screen was handed, see CampfireApp,
            // and only once it reaches above the navigation bar it covers, which is when the padding outgrows that bar:
            // the viewport then ends at the keyboard, so the caret the field brings into view is never behind it.
            .padding(EditorFieldPadding(contentPadding = contentPadding, restingBottomInset = restingBottomInset))
            .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
            .onGloballyPositioned { viewportTop = it.positionOnScreen().y }
            // The field's own scrolling cannot hold any room after the last line, so it is never given less height than
            // its text and the scrolling is a container around it, one per axis. The vertical one hands its viewport
            // height on as the minimum, so a short song is still a field down to the bottom of the pane. The sideways
            // one measures the field against an unbounded width, which is what keeps the text from wrapping, and
            // still passes the pane's width on as the minimum, so the whole pane stays the field and a press
            // anywhere in it places the caret. The field asks its ancestors to bring the caret into view as it moves,
            // so both follow the typing on their own.
            .bounceVerticalScroll(scrollState)
            .bounceHorizontalScroll(horizontalScrollState)
            .then(fieldModifier),
        state = textFieldState,
        // The landscape keyboard leaves the smallest phone about 150 dp, much of it taken by the 48 dp title row and the
        // field's padding: a tighter leading fits three lines in what is left without making the letters any smaller.
        textStyle = bodyLarge.copy(
            lineHeight = if (isCompactTyping) 18.sp else bodyLarge.lineHeight,
            fontFamily = LocalMonospaceFontFamily.current,
            color = colorScheme.onSurface,
        ),
        // Autocorrect and automatic capitalization fight with a format whose words are "[Am]" and "{start_of_verse}".
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        lineLimits = TextFieldLineLimits.MultiLine(),
        outputTransformation = outputTransformation,
        onTextLayout = { getResult -> textLayout.getResult = getResult },
        cursorBrush = SolidColor(colorScheme.primary),
        // The margins are part of the field rather than of the scrolling around it, so that the field starts at the
        // edge of what it is scrolled in: the focus brings a field wider than the pane into view by its start edge,
        // which would scroll a margin outside it out of sight on every tap that places the caret.
        decorator = { innerTextField ->
            Box(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = restingBottomInset + 32.dp + with(density) { coveredRoom.toDp() },
                ),
                propagateMinConstraints = true,
            ) {
                innerTextField()
            }
        },
    )
}

/**
 * The keyboard's part of the padding the editor's field is handed, asked for while the field is laid out rather than
 * while it is composed: it changes on every frame the keyboard slides for, and reading it while composing would
 * compose the whole field again on each of those frames.
 *
 * The handed padding never falls below the navigation bar, [restingBottomInset], which the field reaches under, so
 * until the keyboard rises past that bar there is nothing to take off; from then on it is the keyboard's height
 * whole, since the keyboard covers the bar rather than pushing it up.
 */
@Stable
private class EditorFieldPadding(
    private val contentPadding: PaddingValues,
    private val restingBottomInset: Dp,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateBottomPadding() = contentPadding.calculateBottomPadding().let { if (it > restingBottomInset) it else 0.dp }
}

/** The field's text layout as it last reported it, kept outside the snapshot system since only [revealOffset] reads it. */
private class TextLayoutHolder {
    var getResult: () -> TextLayoutResult? = { null }
}

/** Waits until what was changed in this frame has been laid out: the next frame starts before its layout. */
private suspend fun awaitLayout() = repeat(2) { withFrameNanos { } }

/**
 * Scrolls the line holding [offset] into the top [uncoveredHeight] of the pane, the part above the sheet over the field,
 * unless it is there already (see [revealTarget]). The vertical scroll and the sideways one are containers around the field (see [ChordProTextField]), so its layout's
 * coordinates are theirs once the margin before the text is added.
 */
private suspend fun revealOffset(
    offset: Int,
    layout: TextLayoutResult,
    scrollState: ScrollState,
    horizontalScrollState: ScrollState,
    textStart: Float,
    uncoveredHeight: Float,
    margin: Float,
) = coroutineScope {
    val line = layout.getLineForOffset(offset.coerceAtMost(layout.layoutInput.text.length))
    val top = layout.getLineTop(line)
    val bottom = layout.getLineBottom(line)
    revealTarget(lineTop = top, lineBottom = bottom, scroll = scrollState.value, uncoveredHeight = uncoveredHeight, margin = margin)?.let {
        launch { scrollState.animateScrollTo(it) }
    }
    val x = textStart + layout.getHorizontalPosition(offset, usePrimaryDirection = true)
    if (x < horizontalScrollState.value + margin || x > horizontalScrollState.value + horizontalScrollState.viewportSize - margin) {
        launch { horizontalScrollState.animateScrollTo((x - margin).roundToInt().coerceAtLeast(0)) }
    }
}

private const val UNCOVERED_HEIGHT_SETTLING_MILLIS = 150L
private val REVEAL_MARGIN = 48.dp
