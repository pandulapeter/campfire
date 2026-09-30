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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import kotlinx.coroutines.launch

/**
 * Drives the song details screen from the arrow keys: Up and Down scroll the song being read, Left and Right step
 * to the previous and the next song of the setlist. That is a keyboard on the desktop and the web, and it is also
 * a page turner pedal paired with a phone or a tablet, which is exactly what those send. Where the song has its buttons
 * that step through it - between its rows where it is read across the columns, between its sections otherwise - Up and
 * Down press those instead of scrolling, and so do Page Up and Page Down, which the other kind of pedal sends: a pedal
 * is pressed while both hands are on the instrument, and a press that nudges the song by a tenth of the screen would
 * leave the reader halfway through a verse.
 *
 * The screen takes focus as it opens, because nothing on it would otherwise ever be focused and key events only
 * travel along the focus path. The handler sits in the preview pass rather than the bubbling one so that it sees
 * the event wherever inside the screen the focus has since moved to - an app bar button that was clicked, the
 * scrolling content itself - and so that the scrolling container underneath never gets to interpret an arrow of
 * its own. Consuming all four takes two dimensional focus traversal away from this screen, which is the trade: the
 * keys are worth more to a reader here than they are to Tab, which still traverses everything. Only the four on their
 * own: an arrow with Alt, Meta or Ctrl is somebody else's shortcut - the browser's Back among them - and is left
 * unconsumed.
 *
 * A step key held down is one step, however many repeats the system sends while it is: a foot resting on a pedal is
 * not a request to page, and pages stepped past at the speed of a key repeat are never on screen at rest. So a step
 * key is acted on at its first press only, until it is released, the window losing the focus counting as a release
 * since the release then goes to another one. Where AWT on Linux delivers a repeat as a release and a press (X11
 * without detectable auto-repeat), a held key still steps per repeat. A scroll keeps taking every repeat, which is
 * what makes a held arrow scroll continuously.
 *
 * @param onPreviousSong Null when the current song is the first one, or when there is only the one to read; the
 *   event is then left alone rather than swallowed.
 * @param onNextSong Null when the current song is the last one, the same way.
 * @param onStepBack What the step buttons' previous button does - the previous section or row, or at the first one the
 *   previous song of a setlist - or null where that button is not there, which leaves Up to scroll and Page Up alone.
 * @param onStepForward The same for the next button, Down and Page Down.
 * @param isUncovered Whether no dialog, sheet or other screen is drawn over this one. The screen takes the focus as
 *   soon as this holds, and back whenever it loses it while this holds and no overflow menu is open: focus that went to
 *   nothing - a focused chip on a page the pager let go of, anything focused that left the screen - leaves no path for
 *   a key to travel, and Compose then spends the next arrow on a focus search of its own, which scrolls the song
 *   instead of stepping it. Anything drawn over the screen keeps the focus it took for as long as it is there.
 */
@Composable
internal fun Modifier.songKeyboardShortcuts(
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onPreviousSong: (() -> Unit)?,
    onNextSong: (() -> Unit)?,
    onStepBack: (() -> Unit)?,
    onStepForward: (() -> Unit)?,
    isUncovered: Boolean,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val latestIsUncovered by rememberUpdatedState(isUncovered)
    LaunchedEffect(isUncovered) { if (isUncovered) focusRequester.requestFocus() }
    // Only the handler reads it, so it is no state.
    val heldStepKeys = remember { mutableSetOf<Key>() }
    // The focus inside a window stays where it was when the window loses it, so that is not reported below.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) { snapshotFlow { windowInfo.isWindowFocused }.collect { if (!it) heldStepKeys.clear() } }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            if (!focusState.hasFocus) heldStepKeys.clear()
            if (!focusState.hasFocus) coroutineScope.launch {
                // Whatever took the focus, if anything, has it by the next frame, and so has whatever it is drawn by.
                withFrameNanos {}
                if (latestIsUncovered && !isAnyOverflowMenuOpen) focusRequester.requestFocus()
            }
        }
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
            // Before the check for modifiers below, so that a key released with one of them down is still released.
            if (keyEvent.type == KeyEventType.KeyUp) heldStepKeys.remove(keyEvent.key)
            // Key repeats arrive as further KeyDown events, which is what makes a held arrow scroll continuously.
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // An arrow pressed with Alt, Meta or Ctrl belongs to whoever sent it rather than to the reader: Alt + Left
            // is the browser's Back, which on the web is this app's own back stack, and Cmd + Left and Cmd + Right are
            // Back and Forward on a Mac. A page turner pedal sends the arrows on their own, so nothing is lost by
            // leaving those presses alone. Shift is not among them: it modifies a selection, and there is nothing on
            // this screen to select.
            if (keyEvent.isAltPressed || keyEvent.isMetaPressed || keyEvent.isCtrlPressed) return@onPreviewKeyEvent false
            val step = when (keyEvent.key) {
                Key.DirectionUp, Key.PageUp -> onStepBack
                Key.DirectionDown, Key.PageDown -> onStepForward
                else -> null
            }
            if (step != null) {
                if (heldStepKeys.add(keyEvent.key)) step()
                return@onPreviewKeyEvent true
            }
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
