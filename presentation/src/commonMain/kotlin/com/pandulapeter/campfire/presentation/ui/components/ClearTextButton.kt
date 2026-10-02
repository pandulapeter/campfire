/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.songs_clear
import org.jetbrains.compose.resources.painterResource

/**
 * The trailing icon of a text field that empties it, which is only there while the field has something in it, and
 * scales and fades in and out the way the list screens' search does it.
 *
 * It is null while the field is empty and the button has finished leaving, so that it is handed to the field's
 * `trailingIcon` as it is: Material keeps a touch target's width for the slot whether or not anything is drawn in it,
 * and a field that always had one would give up that width to an empty corner, which leaves the year next to the
 * album no room for four digits. The slot comes with the first character and goes with the last, so the text it
 * makes room for is never long enough to be moved by it.
 */
@Composable
internal fun rememberClearTextButton(
    isVisible: Boolean,
    onClear: () -> Unit,
): (@Composable () -> Unit)? {
    // Starting from where the field is rather than from empty, so that a field opened on text has its button from the
    // first frame instead of scaling it in.
    val visibleState = remember { MutableTransitionState(isVisible) }
    visibleState.targetState = isVisible
    if (!isVisible && !visibleState.currentState && visibleState.isIdle) return null
    return {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            // The text field would otherwise show the text cursor over the button on desktop. It takes no focus, so that
            // the keyboard's Next and Tab go from field to field rather than stopping at the button of the field they
            // are leaving.
            IconButton(
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                    .focusProperties { canFocus = false },
                onClick = onClear,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_clear),
                    contentDescription = stringResource(Res.string.songs_clear),
                )
            }
        }
    }
}
