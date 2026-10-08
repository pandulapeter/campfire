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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Lets a dialog's confirmation through the first time and never again. A dialog that confirms does not leave with
 * the tap but with the next frame, and until then its button and the keyboard's Done key are both still live: on a
 * frame that comes late a second tap lands on a dialog that has already been answered, and creates the song or
 * the setlist a second time. The state is written as the event is handled, so the second event of the same frame
 * already reads it. That is the keyboard's Done and the button landing on the same frame; a Done that comes after the
 * sheet has started closing is dropped by the form itself, through [LocalIsSheetClosing]. The sheet's close after a
 * confirmation cannot be taken back ([CampfireBottomSheet]), so the one confirmation let through is never left on a
 * sheet that stays up.
 */
@Composable
internal fun rememberSingleConfirmation(): (confirm: () -> Unit) -> Unit {
    var hasConfirmed by remember { mutableStateOf(false) }
    return { confirm ->
        if (!hasConfirmed) {
            hasConfirmed = true
            confirm()
        }
    }
}
