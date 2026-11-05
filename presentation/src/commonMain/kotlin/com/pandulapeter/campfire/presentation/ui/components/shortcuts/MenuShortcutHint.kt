/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.isolatedLeftToRight

/**
 * [shortcut]'s keys at the end of a menu entry (a `DropdownMenuItem`'s trailing slot), or null where hints are not
 * shown, so that the slot stays empty. Quieter than the entry's name, and left to right in every language, as the keys
 * are in Hebrew's menus too.
 */
@Composable
internal fun menuShortcutHint(shortcut: KeyShortcut?): (@Composable () -> Unit)? {
    val hint = shortcut?.hint() ?: return null
    return {
        Text(
            text = hint.isolatedLeftToRight(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
