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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * The [FocusRequester] of the field a dialog opens onto. A dialog that is there to be typed into puts the caret in
 * its first field rather than asking for one more tap, which on a touch platform is also what brings the keyboard
 * up with it - and every such dialog here has one field that obviously comes first (its first field, or its only one,
 * under whatever the dialog has to say before it), so there is only ever the one field to open on. The forms that are opened to be looked over as much as to be typed into are the exception,
 * opening on none of their fields: the song metadata form, and a setlist's details being edited. Both assignment
 * sheets leave their search fields unfocused.
 *
 * @param isFocused Whether the field is given the caret as the dialog opens, for a dialog that does so only some of the
 *   ways it is opened.
 */
@Composable
internal fun rememberFirstFieldFocusRequester(isFocused: Boolean = true): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isFocused) focusRequester.requestFocus() }
    return focusRequester
}
