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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.ui.components.horizontalArrowStep
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import kotlinx.coroutines.launch

/**
 * Drives the song details screen from the arrow keys: Down and Right go on through the song being read, Up and Left go
 * back, and with Shift held they switch to the previous or the next song of a setlist instead. That is a keyboard on
 * the desktop and the web, and it is also a page turner pedal paired with a phone or a tablet, which sends either pair of
 * arrows depending on how it is set, so both pairs mean the same. Where the song has its buttons that step through it -
 * between its rows where it is read across the columns, between its sections otherwise - the arrows press those instead
 * of scrolling, and so do Page Up and Page Down, which the other kind of pedal sends: a pedal is pressed while both hands
 * are on the instrument, and a press that nudges the song by a tenth of the screen would leave the reader halfway
 * through a verse. Stepping past a song's end or start goes on to its neighbour in a setlist as the buttons do.
 *
 * Left and Right follow the song: in a strip turned a column at a time, Left goes on where the strip reads from the
 * right ([songReadsLeftwards]), as its buttons and taps do; a song read down the screen follows the layout's direction.
 * Shift + Left and Right follow the pager instead ([horizontalArrowStep]), which lays its songs out from the right in a
 * right-to-left layout, as its bar's buttons do. Shift + an arrow skips the question a step past the song's end asks
 * (`NextSongConfirmation`), being a jump asked for rather than a pedal pressed once too often, and is taken outside a
 * setlist too, doing nothing.
 *
 * The screen takes focus as it opens, because nothing on it would otherwise ever be focused and key events only
 * travel along the focus path. The handler sits in the preview pass rather than the bubbling one so that it sees
 * the event wherever inside the screen the focus has since moved to - an app bar button that was clicked, the
 * scrolling content itself - and so that the scrolling container underneath never gets to interpret an arrow of
 * its own. Consuming the arrows takes two dimensional focus traversal away from this screen, which is the trade: the
 * keys are worth more to a reader here than they are to Tab, which still traverses everything. Only the arrows on
 * their own or with Shift: an arrow with Alt, Meta or Ctrl is somebody else's shortcut - the browser's Back among them -
 * and is left unconsumed.
 *
 * A step key held down is one step, however many repeats the system sends while it is: a foot resting on a pedal is
 * not a request to page, and pages stepped past at the speed of a key repeat are never on screen at rest. So a step
 * key is acted on at its first press only, until it is released, the window losing the focus counting as a release
 * since the release then goes to another one. Where AWT on Linux delivers a repeat as a release and a press (X11
 * without detectable auto-repeat), a held key still steps per repeat. A scroll keeps taking every repeat, which is
 * what makes a held arrow scroll continuously.
 *
 * Space plays and stops a song that scrolls by itself ([onToggleAutoScroll]), once a press, as a step key steps once.
 *
 * Ctrl / Cmd + E opens the editor, the way the overflow menu's Edit does, so not in performance mode, where there is none.
 * Not with Alt held, since AltGr arrives as Ctrl + Alt on Windows and the web and types a character on some layouts.
 *
 * Ctrl / Cmd + Shift + Up and Down step the transposition ([onTranspose]) as its stepper does, a held key repeating as
 * the held button does; T taps the tempo ([onTapTempo]) once a press, as Space plays once.
 *
 * @param onToggleAutoScroll Plays or stops the song scrolling by itself, null where it is laid out in pages; Space is
 *   then left alone.
 * @param onEdit Opens the editor on the song being read, or null where it cannot be edited; the key is then left alone.
 * @param onSave Ctrl / Cmd + S, for a preview of songs an import has not written yet to press its button with, as the
 *   import sheets' and the editor's Save are pressed, and for a song of the library to open Share or export with, as
 *   its menu entry says; null where there is neither, which leaves the key alone.
 * @param onPreviousSong Shift + Up, or the Shift + Left or Right lying that way; null when the current song is the first
 *   one, or when there is only the one to read.
 * @param onNextSong Null when the current song is the last one, the same way.
 * @param onStepBack What the step buttons' previous button does - the previous section or row, or at the first one the
 *   previous song of a setlist - or null where that button is not there, which leaves the arrows going back to scroll
 *   and Page Up alone.
 * @param onStepForward The same for the next button, the arrows going on, and Page Down.
 * @param songReadsLeftwards Whether the song is a strip read from the right, null where it is no strip.
 * @param isUncovered Whether no dialog, sheet or other screen is drawn over this one. The screen takes the focus as
 *   soon as this holds, and back whenever it loses it while this holds and no overflow menu is open: focus that went to
 *   nothing - a focused chip on a page the pager let go of, anything focused that left the screen - leaves no path for
 *   a key to travel, and Compose then spends the next arrow on a focus search of its own, which scrolls the song
 *   instead of stepping it. Anything drawn over the screen keeps the focus it took for as long as it is there. While
 *   this does not hold the screen's keys are swallowed rather than acted on, since something drawn in the window over
 *   the screen (the export screen) does not take the focus by being there.
 */
@Composable
internal fun Modifier.songKeyboardShortcuts(
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onPreviousSong: (() -> Unit)?,
    onNextSong: (() -> Unit)?,
    onStepBack: (() -> Unit)?,
    onStepForward: (() -> Unit)?,
    songReadsLeftwards: Boolean?,
    isUncovered: Boolean,
    onToggleAutoScroll: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onTranspose: ((semitones: Int) -> Unit)?,
    onTapTempo: (() -> Unit)?,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val latestIsUncovered by rememberUpdatedState(isUncovered)
    // Only the handler reads it, so it is no state.
    val heldStepKeys = remember { mutableSetOf<Key>() }
    // A key held as the cover went up is released to whatever covers the screen, so it is not taken for held here.
    LaunchedEffect(isUncovered) { if (!isUncovered) heldStepKeys.clear() else focusRequester.requestFocus() }
    // The focus inside a window stays where it was when the window loses it, so that is not reported below.
    val windowInfo = LocalWindowInfo.current
    val overlayState = LocalOverlayState.current
    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(windowInfo) { snapshotFlow { windowInfo.isWindowFocused }.collect { if (!it) heldStepKeys.clear() } }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            if (!focusState.hasFocus) heldStepKeys.clear()
            if (!focusState.hasFocus) coroutineScope.launch {
                // Whatever took the focus, if anything, has it by the next frame, and so has whatever it is drawn by.
                withFrameNanos {}
                if (latestIsUncovered && !overlayState.isAnyMenuOpen) focusRequester.requestFocus()
            }
        }
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
            // Before the check for modifiers below, so that a key released with one of them down is still released.
            if (keyEvent.type == KeyEventType.KeyUp) heldStepKeys.remove(keyEvent.key)
            if (onEdit != null && KeyShortcut.EDIT.matches(keyEvent)) {
                // The key-up is taken too, so that nothing under this sees half a shortcut.
                if (keyEvent.type == KeyEventType.KeyDown && latestIsUncovered && !overlayState.isValueBeingTyped) onEdit()
                return@onPreviewKeyEvent true
            }
            if (onSave != null && KeyShortcut.SAVE.matches(keyEvent)) {
                if (keyEvent.type == KeyEventType.KeyDown && latestIsUncovered && !overlayState.isValueBeingTyped) onSave()
                return@onPreviewKeyEvent true
            }
            val transposition = when {
                KeyShortcut.TRANSPOSE_UP.matches(keyEvent) -> 1
                KeyShortcut.TRANSPOSE_DOWN.matches(keyEvent) -> -1
                else -> null
            }
            if (onTranspose != null && transposition != null) {
                if (keyEvent.type == KeyEventType.KeyDown && latestIsUncovered && !overlayState.isValueBeingTyped) onTranspose(transposition)
                return@onPreviewKeyEvent true
            }
            // Key repeats arrive as further KeyDown events, which is what makes a held arrow scroll continuously.
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // An arrow pressed with Alt, Meta or Ctrl belongs to whoever sent it rather than to the reader: Alt + Left
            // is the browser's Back, which on the web is this app's own back stack, and Cmd + Left and Cmd + Right are
            // Back and Forward on a Mac. A page turner pedal sends the arrows on their own, so nothing is lost by
            // leaving those presses alone. Shift is not among them: it modifies a selection, and there is nothing on
            // this screen to select.
            if (keyEvent.isAltPressed || keyEvent.isMetaPressed || keyEvent.isCtrlPressed) return@onPreviewKeyEvent false
            // A tempo being typed into takes the arrows for its caret and its steps.
            if (overlayState.isValueBeingTyped) return@onPreviewKeyEvent false
            // Consumed rather than left alone while the screen is covered: an arrow nobody consumes is spent by Compose on
            // a focus search of its own, which would move the focus to a chip or a step button of the covered song and
            // scroll it into view.
            if (!latestIsUncovered) return@onPreviewKeyEvent STEP_AND_SCROLL_KEYS.any { it.matches(keyEvent) }
            if (onTapTempo != null && KeyShortcut.TAP_TEMPO.matches(keyEvent)) {
                if (heldStepKeys.add(keyEvent.key)) onTapTempo()
                return@onPreviewKeyEvent true
            }
            if (onToggleAutoScroll != null && KeyShortcut.PLAY.matches(keyEvent)) {
                if (heldStepKeys.add(keyEvent.key)) onToggleAutoScroll()
                return@onPreviewKeyEvent true
            }
            val songStep = when {
                KeyShortcut.SONG_SWITCH_UP.matches(keyEvent) -> -1
                KeyShortcut.SONG_SWITCH_DOWN.matches(keyEvent) -> 1
                KeyShortcut.SONG_SWITCH_LEFT.matches(keyEvent) || KeyShortcut.SONG_SWITCH_RIGHT.matches(keyEvent) ->
                    keyEvent.key.horizontalArrowStep(layoutDirection)
                else -> null
            }
            if (songStep != null) {
                // Taken outside a setlist too, so that a Shift held by accident never turns a page instead.
                val switch = if (songStep < 0) onPreviousSong else onNextSong
                if (switch != null && heldStepKeys.add(keyEvent.key)) switch()
                return@onPreviewKeyEvent true
            }
            val isPageKey = KeyShortcut.SONG_PAGE_UP.matches(keyEvent) || KeyShortcut.SONG_PAGE_DOWN.matches(keyEvent)
            val direction = when {
                KeyShortcut.SONG_UP.matches(keyEvent) || KeyShortcut.SONG_PAGE_UP.matches(keyEvent) -> -1
                KeyShortcut.SONG_DOWN.matches(keyEvent) || KeyShortcut.SONG_PAGE_DOWN.matches(keyEvent) -> 1
                KeyShortcut.SONG_LEFT.matches(keyEvent) || KeyShortcut.SONG_RIGHT.matches(keyEvent) ->
                    keyEvent.key.horizontalArrowStep(if (songReadsLeftwards ?: (layoutDirection == LayoutDirection.Rtl)) LayoutDirection.Rtl else LayoutDirection.Ltr)
                else -> null
            } ?: return@onPreviewKeyEvent false
            val step = if (direction < 0) onStepBack else onStepForward
            when {
                step != null -> if (heldStepKeys.add(keyEvent.key)) step()
                isPageKey -> return@onPreviewKeyEvent false
                direction < 0 -> onScrollUp()
                else -> onScrollDown()
            }
            true
        }
}

private val STEP_AND_SCROLL_KEYS = listOf(
    KeyShortcut.SONG_UP,
    KeyShortcut.SONG_DOWN,
    KeyShortcut.SONG_PAGE_UP,
    KeyShortcut.SONG_PAGE_DOWN,
    KeyShortcut.SONG_LEFT,
    KeyShortcut.SONG_RIGHT,
    KeyShortcut.SONG_SWITCH_UP,
    KeyShortcut.SONG_SWITCH_DOWN,
    KeyShortcut.SONG_SWITCH_LEFT,
    KeyShortcut.SONG_SWITCH_RIGHT,
)
