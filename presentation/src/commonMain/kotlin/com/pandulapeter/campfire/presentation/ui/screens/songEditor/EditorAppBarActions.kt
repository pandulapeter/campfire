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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_redo
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.ic_undo
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
import com.pandulapeter.campfire.presentation.resources.song_editor_undo
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import org.jetbrains.compose.resources.painterResource

/** The editor's title row actions: the Shortcuts chevron where the control row has given way, undo, redo, Save and the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.EditorAppBarActions(
    textFieldState: TextFieldState,
    isToolbarToggleShown: Boolean,
    isToolbarExpanded: Boolean,
    onToolbarToggled: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    menuMaxWidth: Dp,
    onFind: () -> Unit,
    onFindAndReplace: () -> Unit,
    onOpenSongInfo: () -> Unit,
    canPrettify: () -> Boolean,
    onPrettify: () -> Unit,
    canRevert: Boolean,
    onRevert: () -> Unit,
    onOpenChordProReference: () -> Unit,
) {
    AnimatedVisibility(
        // Outside the animated width (see overlappingAction's KDoc), and at its end: it is the first action,
        // and the undo button after it is drawn last, so a press on the overlap still reaches undo.
        modifier = Modifier.overlappingAction(start = 0.dp, end = ACTION_BUTTON_OVERLAP),
        visible = isToolbarToggleShown,
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut(),
    ) {
        EditorToolbarToggle(
            isExpanded = isToolbarExpanded,
            isLabeled = false,
            isEnabled = true,
            onToggled = onToolbarToggled,
        )
    }
    IconButton(
        enabled = textFieldState.undoState.canUndo,
        onClick = { textFieldState.undoState.undo() },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_undo),
            contentDescription = stringResource(Res.string.song_editor_undo),
        )
    }
    IconButton(
        modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
        enabled = textFieldState.undoState.canRedo,
        onClick = { textFieldState.undoState.redo() },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_redo),
            contentDescription = stringResource(Res.string.song_editor_redo),
        )
    }
    IconButton(
        modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
        enabled = canSave,
        onClick = onSave,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_save),
            contentDescription = stringResource(Res.string.save),
        )
    }
    EditorMenu(
        modifier = Modifier
            .overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp)
            .widthIn(max = menuMaxWidth),
        onFind = onFind,
        onFindAndReplace = onFindAndReplace,
        onOpenSongInfo = onOpenSongInfo,
        canPrettify = canPrettify,
        onPrettify = onPrettify,
        canRevert = canRevert,
        onRevert = onRevert,
        onOpenChordProReference = onOpenChordProReference,
    )
}
