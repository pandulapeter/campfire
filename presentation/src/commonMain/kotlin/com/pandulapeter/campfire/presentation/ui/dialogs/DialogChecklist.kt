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

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * What Material's `AlertDialog` pads its content by on every side. Material narrows it for a precision pointer, but only
 * behind a flag the app does not turn on.
 */
private val DIALOG_CONTENT_PADDING = 24.dp

/**
 * What lines the checkboxes of a dialog's checklist up with the edge of the field above them: the list item keeps
 * 16 dp at its start and the checkbox draws its box 2 dp inside its own bounds, which leaves this much of the dialog's
 * padding for the row to add.
 */
internal val DIALOG_CHECKLIST_ROW_INSET = DIALOG_CONTENT_PADDING - 18.dp

/**
 * A dialog's checklist running out over the dialog's padding to both of its edges, so that a row lights up across the
 * whole dialog when it is pressed, the way it does in a sheet, while reporting only the width of the content around
 * it so that everything else is laid out as it would be without it. The rows keep their content in line with the rest
 * of the dialog's with [DIALOG_CHECKLIST_ROW_INSET].
 */
internal fun Modifier.reachingDialogEdges() = layout { measurable, constraints ->
    val outset = DIALOG_CONTENT_PADDING.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = outset * 2))
    layout((placeable.width - outset * 2).coerceAtLeast(0), placeable.height) {
        placeable.placeRelative(-outset, 0)
    }
}
