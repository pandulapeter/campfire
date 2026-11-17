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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged

/**
 * What Ctrl / Cmd + S presses in a [CampfireBottomSheet]: the [BottomSheetConfirmButton] in its header that writes
 * what the sheet is for, which sets itself here while it is there. Null where the sheet has no such button, or one the
 * key should not press.
 */
internal class SheetSaveShortcut {
    var action: (() -> Unit)? = null
}

/** Whether anything in a [CampfireBottomSheet] has the focus, read once from an effect rather than drawn from. */
internal class SheetFocusState {
    var hasFocus = false
}

/**
 * The focus target of a [CampfireBottomSheet]'s column. A sheet that opens onto none of its fields would otherwise hold
 * no focus, and a key only travels along the focus path, so Ctrl / Cmd + S would go unheard until something in it was
 * clicked. A bare focus target draws nothing and brings no keyboard up. It is taken a frame in, and only where nothing
 * in the sheet has the focus by then: a form's first field asks for it from an effect of its own, and the order the two
 * effects run in is no promise of which one ends up with it.
 */
@Composable
internal fun rememberSheetFocusTarget(): Modifier {
    val sheetFocus = remember { FocusRequester() }
    val sheetFocusState = remember { SheetFocusState() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!sheetFocusState.hasFocus) sheetFocus.requestFocus()
    }
    return remember {
        Modifier
            .onFocusChanged { sheetFocusState.hasFocus = it.hasFocus }
            .focusRequester(sheetFocus)
            .focusTarget()
    }
}

/** The [SheetSaveShortcut] of the [CampfireBottomSheet] around it, null outside one. */
internal val LocalSheetSaveShortcut = staticCompositionLocalOf<SheetSaveShortcut?> { null }
