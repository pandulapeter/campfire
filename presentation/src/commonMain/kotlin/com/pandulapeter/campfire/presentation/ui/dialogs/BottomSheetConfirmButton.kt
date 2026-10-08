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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The filled confirmation action shared by forms in a sheet's header. It does nothing once the sheet has started
 * closing ([LocalIsSheetClosing]), and keeps its look while it slides away with it.
 *
 * @param isSaveShortcut Whether Ctrl / Cmd + S presses it too ([LocalSheetSaveShortcut]), which every Save, Create and
 *   Done does; an action that destroys something is not pressed by a key that only ever means keeping it.
 */
@Composable
internal fun BottomSheetConfirmButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSaveShortcut: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable () -> Unit,
) {
    val isClosing = LocalIsSheetClosing.current
    val saveShortcut = LocalSheetSaveShortcut.current
    if (saveShortcut != null && isSaveShortcut) {
        val currentOnClick by rememberUpdatedState(onClick)
        val isEnabled by rememberUpdatedState(enabled)
        DisposableEffect(saveShortcut) {
            val action = { if (isEnabled && !isClosing()) currentOnClick() }
            saveShortcut.action = action
            onDispose { if (saveShortcut.action === action) saveShortcut.action = null }
        }
    }
    Button(
        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        onClick = { if (!isClosing()) onClick() },
        enabled = enabled,
        colors = colors,
    ) { content() }
}
