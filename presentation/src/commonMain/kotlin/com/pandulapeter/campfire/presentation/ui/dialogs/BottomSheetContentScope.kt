/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.resources.close

/**
 * Whether the [CampfireBottomSheet] around it has started closing - its close button, a Save that closes it, a swipe or
 * the scrim. The sheet stays on screen and live for the length of its slide, and a Save tapped in it would write the
 * draft the close button had just cancelled, so the actions that write ask this first. It is a function rather than a
 * value, read at the tap, so that nothing recomposes on the frames of the slide.
 */
internal val LocalIsSheetClosing = compositionLocalOf<() -> Boolean> { { false } }

/**
 * Whether the dialog a [CampfireBottomSheet] is drawn for has been asked to close by the stack of open dialogs it is in,
 * which it does as if one of its own actions had closed it, see `DialogHost.closeRequest`.
 */
internal val LocalIsSheetCloseRequested = staticCompositionLocalOf { false }

/**
 * The column of a [CampfireBottomSheet], which its content can also close the sheet from.
 *
 * @param uncoveredTopInset How much of the top inset the sheet is not padded by at the moment: Material pads it by the
 *   part of the inset its top edge has come into, so the height its content is offered changes as it slides up and is
 *   dragged. Content that sizes itself to that height takes this off it to have the height of the sheet at its
 *   tallest, which does not move - or the sheet's height would decide its own offset, and the offset the height.
 */
internal class BottomSheetContentScope(
    columnScope: ColumnScope,
    private val close: () -> Unit,
    val uncoveredTopInset: () -> Dp,
) : ColumnScope by columnScope {

    fun close() = close.invoke()
}
