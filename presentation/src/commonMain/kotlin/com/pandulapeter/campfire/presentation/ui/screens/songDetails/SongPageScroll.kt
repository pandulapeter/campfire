/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.ui.components.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.RowSnapFlingBehavior

/**
 * The scroll a song page is read through: down, its top edge fading what is scrolled past, or sideways for a strip turned
 * a column at a time ([isAcross]), which nothing is scrolled under. A strip's scroll is counted from the song's start
 * edge, as its stops are (`SongRows.isAcross`), so it is reversed where the song [readsLeftwards] and the screen does not,
 * or the other way around. A strip is drawn only up to the step buttons, [endPadding] and their own width from the end
 * of the screen: the column after the ones in view would otherwise show under them, and slides in from their edge.
 */
@Composable
internal fun Modifier.songPageScroll(
    scrollState: ScrollState,
    flingBehavior: RowSnapFlingBehavior,
    isAcross: Boolean,
    readsLeftwards: Boolean,
    endPadding: Dp,
): Modifier = if (isAcross) {
    val isScreenRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    drawWithContent {
        val end = (endPadding + STEP_BUTTON_SIZE + STEP_BUTTON_GAP).toPx()
        clipRect(left = if (isScreenRtl) end else 0f, right = if (isScreenRtl) size.width else size.width - end) { this@drawWithContent.drawContent() }
    }.bounceHorizontalScroll(
        state = scrollState,
        flingBehavior = flingBehavior,
        reverseScrolling = readsLeftwards != isScreenRtl,
    )
} else {
    fadingTopEdge(
        scrolled = { flingBehavior.rows.scrolledIntoRow(scrollState.value) },
        backgroundColor = MaterialTheme.colorScheme.background,
    ).bounceVerticalScroll(state = scrollState, flingBehavior = flingBehavior)
}

/**
 * Whether a song page scrolls sideways: where it [turnsByColumn], from the first frame, before the layout has said
 * whether it made the song a strip, since most songs turned that way are; then for as long as it does. A song read whole,
 * or one with something too tall for a column, scrolls down. Derived, so the page is composed again only when that flips.
 */
@Composable
internal fun rememberIsAcross(flingBehavior: RowSnapFlingBehavior, turnsByColumn: Boolean): Boolean {
    val isAcross by remember(flingBehavior, turnsByColumn) {
        derivedStateOf { turnsByColumn && flingBehavior.rows.let { it.isAcross || it.stepOffsets.isEmpty() } }
    }
    return isAcross
}
