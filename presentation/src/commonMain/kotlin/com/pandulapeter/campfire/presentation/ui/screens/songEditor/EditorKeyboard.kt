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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT

/**
 * What the keyboard leaves the editor, read through states derived from its inset so that it sliding in recomposes the
 * editor only as it crosses a line these care about, not on every frame of its animation.
 *
 * @param isSmallScreen Whether the shorter side of the window is a phone's. The two rows of insertions are taller than
 *   what a phone has left for the text once the keyboard is up, so a phone opens the editor with them folded away. The
 *   shorter side is what decides that rather than the width class: a phone turned sideways is wide enough to count as a
 *   large screen and is the one with the least height of all.
 * @param isControlRowHiddenState Above a phone's landscape keyboard the control row would leave the field a single
 *   line, so there it gives way and only its Shortcuts chevron stays, in the title row; transposing and switching panes
 *   wait for the keyboard to go.
 */
internal class EditorKeyboard(
    val isSmallScreen: Boolean,
    val isKeyboardVisibleState: State<Boolean>,
    val isTypingInShortWindowState: State<Boolean>,
    val isControlRowHiddenState: State<Boolean>,
)

@Composable
internal fun rememberEditorKeyboard(): EditorKeyboard {
    val windowContainerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val isSmallScreen = with(density) { minOf(windowContainerSize.width, windowContainerSize.height).toDp() } < SMALL_SCREEN_SIZE
    val ime = WindowInsets.ime
    val windowHeight = with(density) { windowContainerSize.height.toDp() }
    val isKeyboardVisibleState = remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val isTypingInShortWindowState = remember(ime, density, windowHeight) {
        derivedStateOf { isTypingInShortWindow(imeHeight = with(density) { ime.getBottom(this).toDp() }, windowHeight = windowHeight) }
    }
    val isControlRowHiddenState = remember(ime, density, windowHeight) {
        derivedStateOf { isControlRowHidden(imeHeight = with(density) { ime.getBottom(this).toDp() }, windowHeight = windowHeight) }
    }
    return EditorKeyboard(
        isSmallScreen = isSmallScreen,
        isKeyboardVisibleState = isKeyboardVisibleState,
        isTypingInShortWindowState = isTypingInShortWindowState,
        isControlRowHiddenState = isControlRowHiddenState,
    )
}

/** Whether the keyboard, [imeHeight] tall, leaves a window [windowHeight] tall a short one. */
internal fun isTypingInShortWindow(imeHeight: Dp, windowHeight: Dp) = imeHeight > 0.dp && windowHeight - imeHeight < SHORT_WINDOW_HEIGHT

/** Whether the keyboard, [imeHeight] tall, leaves a window [windowHeight] tall too little for the control row. */
internal fun isControlRowHidden(imeHeight: Dp, windowHeight: Dp) = imeHeight > 0.dp && windowHeight - imeHeight < MIN_ROOM_FOR_CONTROL_ROW

private val SMALL_SCREEN_SIZE = 600.dp

/**
 * The least the keyboard has to leave of the window for the editor's control row to stay over the field: the 48dp
 * compact title row, the 56dp control row and three 18sp lines with the field's padding. The smallest phone keeps
 * about 340dp upright and so keeps the row, and about 112dp on its side and so loses it.
 */
private val MIN_ROOM_FOR_CONTROL_ROW = 240.dp
