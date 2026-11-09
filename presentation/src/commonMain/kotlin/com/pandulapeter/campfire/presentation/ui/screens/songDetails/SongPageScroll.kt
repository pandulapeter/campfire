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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.ui.components.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.RowSnapFlingBehavior
import com.pandulapeter.campfire.presentation.ui.songView.COLUMN_GAP

/**
 * The scroll a song page is read through: down, its top edge fading what is scrolled past, or sideways for a strip turned
 * a column at a time ([isAcross]), which nothing is scrolled under. A strip's scroll is counted from the song's start
 * edge, as its stops are (`SongRows.isAcross`), so it is reversed where the song [readsLeftwards] and the screen does not,
 * or the other way around. A strip fades out at the end of the screen, over the [COLUMN_GAP] it leaves there and the
 * page's [endPadding], painted over in the screen's background, so the column after the ones in view slides in out of
 * the fade. Its step buttons are a row along the bottom (`StepControls`), with nothing at the end edge.
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
    val backgroundColor = MaterialTheme.colorScheme.background
    drawWithContent {
        drawContent()
        val fade = (endPadding + COLUMN_GAP).toPx()
        val (from, to) = if (isScreenRtl) fade to 0f else size.width - fade to size.width
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, backgroundColor), startX = from, endX = to),
            topLeft = Offset(minOf(from, to), 0f),
            size = Size(fade, size.height),
        )
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
