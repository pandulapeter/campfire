/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

/**
 * Keeps a mouse wheel or a trackpad scrolling the sheet's content from moving the sheet. Foundation dispatches their
 * steps as user input, as it does a finger's drag, so once the content reaches its start Material drags the sheet down by
 * what is left of each step. A finger lets go and the sheet settles at an anchor; a wheel never lets go, so the sheet
 * would stay wherever the last step left it, part of the way down. What the content leaves over of a wheel's steps and
 * of the fling Foundation ends them with is taken here, between the content and the sheet. A finger still drags the
 * sheet by its content, as Material has it.
 *
 * Goes before the content's scroll containers in the sheet's modifiers: it has to see each wheel event (on the initial
 * pass, before they do) and be their nested scroll parent.
 */
@Composable
internal fun Modifier.keepSheetFromWheelScrolls(): Modifier {
    val connection = remember { WheelScrollConnection() }
    return pointerInput(connection) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent(PointerEventPass.Initial).type) {
                    PointerEventType.Scroll -> connection.isWheelScrolling = true
                    PointerEventType.Press -> connection.isWheelScrolling = false
                }
            }
        }
    }.nestedScroll(connection)
}

private class WheelScrollConnection : NestedScrollConnection {
    var isWheelScrolling = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        if (isWheelScrolling && source == NestedScrollSource.UserInput) available else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
        if (isWheelScrolling) available else Velocity.Zero
}
