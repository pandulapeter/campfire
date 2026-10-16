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

import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection

/**
 * What a [SectionHeader]'s row is shorter by than the app bar whose place a header at the top of the list stands in. A
 * section's first cards start under the header's row, and under a row only as tall as the pill they would reach up
 * beside the bar's pill and the New button, which stand lower. So the list keeps this room above its content
 * ([withSectionStartRoom]) rather than every header keeping it below its row: the first card of a section the list
 * rests at - its top, or a header tapped - starts below the bar's buttons, and the rows between sections stay short.
 * A header stuck at the top while its section is read reaches up into the room, where the cards under it are faded out.
 */
internal val SECTION_START_ROOM = LIST_APP_BAR_HEIGHT - SECTION_HEADER_ROW_HEIGHT

/**
 * These paddings with [SECTION_START_ROOM] above, for as much of the list as is still under the bar's buttons: once the
 * bar has filled in and moved the list down out of their way, there is nothing to keep the cards from. Read while the
 * list is laid out, so that the bar's spring changes it without recomposing the list.
 */
internal fun PaddingValues.withSectionStartRoom(appBarOverlap: () -> AppBarOverlap): PaddingValues = SectionStartRoomPadding(
    source = this,
    appBarOverlap = appBarOverlap,
)

@Immutable
private data class SectionStartRoomPadding(
    private val source: PaddingValues,
    private val appBarOverlap: () -> AppBarOverlap,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = source.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = source.calculateTopPadding() + SECTION_START_ROOM * appBarOverlap().coverage

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = source.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = source.calculateBottomPadding()
}
