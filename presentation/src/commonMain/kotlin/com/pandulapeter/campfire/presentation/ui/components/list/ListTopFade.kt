/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.only
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.pandulapeter.campfire.presentation.ui.components.OverscrollPull

/**
 * The fade the two list screens' cards go out in towards the top of the list, where the pinned section header and the
 * app bar's buttons float over them with nothing behind them: the cards are gone entirely under the header's row, so
 * its text is read against the screen's background whatever is scrolled under it, and fade back in below the row.
 *
 * It is drawn by each card rather than over the list, because the sticky headers are items of the same list and an
 * overlay would fade them out with the cards. Each card masks itself with the part of the gradient it overlaps, from
 * where it is laid out relative to the top of the list ([listTopFadeViewport]), and only while it overlaps it, since the
 * mask needs a layer of its own. It is as strong as the list is scrolled, up to the height it fades over, so a list
 * resting at its top shows its first cards whole - they start right under the first header's row, where the fade
 * would otherwise begin - and the fade comes in as the list is moved rather than all at once. It is at full strength
 * before a card has come up far enough to reach the header's text.
 *
 * A card under the gradient takes no presses there either (see [fadingUnderListTop]): what is faded out is not what a
 * tap near the header or the buttons is meant for, and a card that can barely be seen should not open on a slip of
 * the finger aimed at a pill.
 *
 * A list too short to scroll is still carried up under the header by the overscroll effect when it is pulled
 * ([overscrollPull], handed to the grid's `bounceScrollableContent`), so the fade follows that pull too: as strong as
 * the list has been pulled, and moved down the cards by as much, since the stretch carries the cards' own drawing up
 * with them.
 *
 * A list whose sections each start with a header of [sectionHeaderContentType] measures the fade from the start of the
 * section being read instead (see [sectionStart]), so a list brought to one of its headers - which is where a
 * tap on a header takes it - shows that section's first card whole, as it shows the first section's at the top.
 */
@Stable
internal class ListTopFade(
    private val listState: LazyGridState,
    private val coveredHeight: () -> Float,
    val fadeHeightPx: Float,
    private val firstCardIndex: Int,
    private val sectionHeaderContentType: Any?,
) {

    val overscrollPull = OverscrollPull()

    /**
     * How far the cards are drawn above where they are laid out. Only a list that cannot scroll is pulled, and a pull
     * left over by a list that has grown scrollable meanwhile, whose release was cut short with it, counts for
     * nothing. Capped at the fade's height, which is as far below it as a card's position is followed.
     */
    val pullPx: Float
        get() = if (listState.canScrollBackward || listState.canScrollForward) 0f else overscrollPull.towardsStart.coerceAtMost(heightPx)

    /** How far down from the top of the list the fade reaches. */
    val coveredHeightPx: Float
        get() = coveredHeight()

    val heightPx: Float
        get() = coveredHeightPx + fadeHeightPx

    /** Where the top of the list is in the window, which the cards place themselves against. */
    var viewportTop by mutableFloatStateOf(0f)

    /**
     * How strong the fade is over a card whose top is [cardTop] below the top of the list, which grows with how far the
     * list has been scrolled from its top. Thinned out near the start of a section for the cards of that section only:
     * the end of the section before, above its header, is still under the row of the header pinned at the top, or in the
     * room above the start of the section the list rests at ([SECTION_START_ROOM]), and is never shown there.
     */
    fun strengthAt(cardTop: Float): Float {
        val strength = listTopFadeStrength(
            canScrollBackward = listState.canScrollBackward,
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            scrollOffset = listState.firstVisibleItemScrollOffset,
            firstCardIndex = firstCardIndex,
            coveredHeightPx = coveredHeightPx,
            fadeHeightPx = fadeHeightPx,
            overscrollPullPx = pullPx,
        )
        if (sectionHeaderContentType == null || strength == 0f) return strength
        val layoutInfo = listState.layoutInfo
        val sectionStart = sectionStart(
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            scrollOffset = listState.firstVisibleItemScrollOffset,
            // A header collapsed to nothing, as the song list's are while its search is open, starts no section
            // anybody can see, and counting it would thin the fade out over every card that follows one.
            headerPositions = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                if (item.contentType == sectionHeaderContentType && item.size.height > 0) item.index to item.offset.y else null
            },
        ) ?: return strength
        // Item offsets start below the content padding, and the cards' tops below the top of the list.
        if (cardTop < sectionStart.top + layoutInfo.beforeContentPadding) return strength
        return minOf(strength, (sectionStart.distance / fadeHeightPx).coerceIn(0f, 1f))
    }
}

/** The covered row recedes as the app bar takes its place; headerless lists fade directly below the bar. */
@Composable
internal fun rememberListTopFade(
    listState: LazyGridState,
    coveredHeightFraction: () -> Float = { 1f },
    firstCardIndex: Int = 0,
    sectionHeaderContentType: Any? = null,
): ListTopFade {
    val density = LocalDensity.current
    return remember(listState, density, coveredHeightFraction, firstCardIndex, sectionHeaderContentType) {
        with(density) {
            ListTopFade(
                listState = listState,
                coveredHeight = { LIST_APP_BAR_HEIGHT.toPx() * coveredHeightFraction().coerceIn(0f, 1f) },
                fadeHeightPx = EDGE_FADE_SIZE.toPx(),
                firstCardIndex = firstCardIndex,
                sectionHeaderContentType = sectionHeaderContentType,
            )
        }
    }
}

/**
 * Measures distance from the real start, including an expanded header but excluding collapsed header slots, or, for a
 * list at its start, how far it has been pulled up past its end.
 */
internal fun listTopFadeStrength(
    canScrollBackward: Boolean,
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    firstCardIndex: Int,
    coveredHeightPx: Float,
    fadeHeightPx: Float,
    overscrollPullPx: Float = 0f,
): Float {
    if (!canScrollBackward) return (overscrollPullPx / fadeHeightPx).coerceIn(0f, 1f)
    if (firstVisibleItemIndex > firstCardIndex) return 1f
    val passedHeader = if (firstCardIndex > 0 && firstVisibleItemIndex == firstCardIndex) coveredHeightPx else 0f
    return ((passedHeader + scrollOffset) / fadeHeightPx).coerceIn(0f, 1f)
}

/**
 * How far the list is from the start of a section, in pixels: scrolled into the header at its top, or short of the
 * next header coming up from below - whichever is nearer, so the fade thins out as the next header reaches the top
 * and comes back as it is scrolled past, rather than dropping away in the frame that header becomes the first item.
 * Null where no section header is on screen but a pinned one, which is a section being read well past its start.
 *
 * @param headerPositions The index and the laid out top of every section header on screen, the pinned one included.
 */
internal fun sectionStart(
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    headerPositions: List<Pair<Int, Int>>,
): SectionStart? = headerPositions.mapNotNull { (index, top) ->
    when {
        // A pinned header is drawn at the top whatever its place, so only how far the list is scrolled into it counts.
        index == firstVisibleItemIndex -> SectionStart(distance = scrollOffset, top = top)
        index > firstVisibleItemIndex -> SectionStart(distance = top.coerceAtLeast(0), top = top)
        else -> null
    }
}.minByOrNull { it.distance }

/** How far the list is from the start of a section ([sectionStart]), and where that section's header is laid out. */
internal data class SectionStart(val distance: Int, val top: Int)

/** Marks the list whose top [fade] is measured from. */
internal fun Modifier.listTopFadeViewport(fade: ListTopFade) = onPlaced { fade.viewportTop = it.positionInWindow().y }

/** Fades a card of the list out as it passes under the top of it, and keeps presses there from reaching it. */
@Composable
internal fun Modifier.fadingUnderListTop(fade: ListTopFade): Modifier {
    val position = remember { CardPosition() }
    return this
        // Clamped to where the fade ends once moved down by the most a pull moves it, so that a card below it writes the
        // same value on every frame of a scroll and its drawing is left alone: only the one or two cards under the fade
        // are drawn again as the list moves.
        .onPlaced { position.top = minOf(it.positionInWindow().y - fade.viewportTop, 2 * fade.heightPx) }
        // The card's own position is read first, so that only a card under the fade reads the scroll offset at all.
        .graphicsLayer {
            compositingStrategy = if (position.top - fade.pullPx < fade.heightPx && fade.strengthAt(position.top) > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithCache {
            // Keep the shader in viewport coordinates and move the canvas beneath it. Rebuilding the gradient
            // with the card's new offset allocated a brush and shader on every frame of a scroll.
            val mask = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = fade.coveredHeightPx,
                endY = fade.heightPx,
            )
            onDrawWithContent {
                drawContent()
                // A pulled list draws its cards higher than they are laid out, so the gradient is drawn as much lower.
                val top = position.top - fade.pullPx
                if (top < fade.heightPx) {
                    val strength = fade.strengthAt(position.top)
                    if (strength > 0f) {
                        translate(top = -top) {
                            drawRect(
                                brush = mask,
                                topLeft = Offset(0f, top),
                                size = size,
                                alpha = strength,
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    }
                }
            }
        }
        .pointerInput(fade) {
            awaitEachGesture {
                // Only the press is taken, before the card sees it, so that the card never starts one there - the
                // list's own scrolling does not ask whether the press was consumed, so a drag started there still
                // scrolls it.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // A card below the fade holds twice the fade's own height as its top, which no press inside it adds up
                // to less than the fade's height, so the clamp leaves the answer as it would be with the real position.
                if (fade.strengthAt(position.top) > 0f && position.top + down.position.y < fade.heightPx) {
                    down.consume()
                }
            }
        }
}

/**
 * Where one card is relative to the top of its list, written as it is placed and read as it is drawn. It is no further
 * down than a pulled fade can reach, since below that the exact position makes no difference to how the card is drawn.
 */
private class CardPosition {

    var top by mutableFloatStateOf(0f)
}
