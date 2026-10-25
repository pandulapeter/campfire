/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Gets the field ready to be typed into where its selection is: the caret in it, the keyboard up, and the selection
 * scrolled into view once the keyboard has settled ([reveals], which [ChordProTextField] answers). It is asked for when
 * the editor opens on a selection and when the toolbar leaves a value to type (`leavesValueToType`). With the keyboard
 * down, the only other way to bring it up is a tap on the field, which puts the caret where the tap landed and so
 * throws away the selection that was made to be typed over. The keyboard is asked for even where the field has the
 * focus already, since one the user put away does not come back with the focus; on the desktop it asks for nothing.
 */
@Stable
internal class TypingRequest(
    private val fieldFocusRequester: FocusRequester,
    private val keyboardController: SoftwareKeyboardController?,
) {
    private val revealRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reveals: Flow<Unit> = revealRequests

    fun request() {
        fieldFocusRequester.requestFocus()
        keyboardController?.show()
        revealRequests.tryEmit(Unit)
    }
}

@Composable
internal fun rememberTypingRequest(fieldFocusRequester: FocusRequester): TypingRequest {
    val keyboardController = LocalSoftwareKeyboardController.current
    return remember(fieldFocusRequester, keyboardController) { TypingRequest(fieldFocusRequester, keyboardController) }
}
