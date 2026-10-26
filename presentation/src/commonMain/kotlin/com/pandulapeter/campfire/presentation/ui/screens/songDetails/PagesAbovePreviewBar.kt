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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The pages, and the preview's card floating over their bottom ([bar], which brings its own margins and is nothing where
 * there is no card). The pages leave free under their last line the room the card takes from the bottom, or [baseBottom]
 * where there is no card, so that every line can still be read above it.
 *
 * The card is measured first, in the same pass as the pages, which get that room on their first frame: the lyrics are
 * flowed into columns against the height left to them, and a room arriving a frame later and animated would flow them
 * again on every frame of the screen's own transition. Only a change of the card's share after that (its height
 * following a page, the card leaving) is animated; [baseBottom] is followed as it is, as it would be without a card.
 */
@Composable
internal fun PagesAbovePreviewBar(
    modifier: Modifier = Modifier,
    baseBottom: Dp,
    bar: @Composable () -> Unit,
    pages: @Composable (bottomPadding: Dp) -> Unit,
) {
    // Written while measuring, read by the pages composed right after it in the same pass.
    val targetClearance = remember { mutableStateOf(0.dp) }
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val barPlaceables = subcompose(PreviewBarSlot.BAR, bar).map { it.measure(loose) }
        val barHeight = barPlaceables.maxOfOrNull { it.height } ?: 0
        targetClearance.value = if (barHeight > 0) (barHeight.toDp() - baseBottom).coerceAtLeast(0.dp) else 0.dp
        val pagePlaceables = subcompose(PreviewBarSlot.PAGES) {
            // The first value is taken as it is, so the pages start out with the room the card already takes.
            val clearance by animateDpAsState(targetClearance.value)
            pages(baseBottom + clearance)
        }.map { it.measure(constraints) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            pagePlaceables.forEach { it.placeRelative(0, 0) }
            barPlaceables.forEach { it.placeRelative((width - it.width) / 2, height - it.height) }
        }
    }
}

private enum class PreviewBarSlot { BAR, PAGES }
