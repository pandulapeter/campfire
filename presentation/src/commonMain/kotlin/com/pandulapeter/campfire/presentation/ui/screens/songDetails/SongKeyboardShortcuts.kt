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

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Drives the song details screen from the arrow keys: Up and Down scroll the song being read, Left and Right step
 * to the previous and the next song of the setlist. That is a keyboard on the desktop and the web, and it is also
 * a page turner pedal paired with a phone or a tablet, which is exactly what those send.
 *
 * The screen takes focus as it opens, because nothing on it would otherwise ever be focused and key events only
 * travel along the focus path. The handler sits in the preview pass rather than the bubbling one so that it sees
 * the event wherever inside the screen the focus has since moved to - an app bar button that was clicked, the
 * scrolling content itself - and so that the scrolling container underneath never gets to interpret an arrow of
 * its own. Consuming all four takes two dimensional focus traversal away from this screen, which is the trade: the
 * keys are worth more to a reader here than they are to Tab, which still traverses everything.
 *
 * @param onPreviousSong Null when the current song is the first one, or when there is only the one to read; the
 *   event is then left alone rather than swallowed.
 * @param onNextSong Null when the current song is the last one, the same way.
 */
@Composable
internal fun Modifier.songKeyboardShortcuts(
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onPreviousSong: (() -> Unit)?,
    onNextSong: (() -> Unit)?,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
            // Key repeats arrive as further KeyDown events, which is what makes a held arrow scroll continuously.
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val action = when (keyEvent.key) {
                Key.DirectionUp -> onScrollUp
                Key.DirectionDown -> onScrollDown
                Key.DirectionLeft -> onPreviousSong
                Key.DirectionRight -> onNextSong
                else -> null
            } ?: return@onPreviewKeyEvent false
            action()
            true
        }
}
