/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.nativeKeyCode
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyChord
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.systemShortcutTexts
import com.pandulapeter.campfire.presentation.ui.dialogs.shortcuts.shortcutFeatures

/**
 * Hands the shortcuts that work here to [onChanged] as the system's keyboard shortcuts list (Meta + /, the activity's
 * `onProvideKeyboardShortcuts`) takes them, in the app's language, again whenever the language or a feature they
 * depend on changes: the system asks the activity at the moment the list is opened, and the strings are composition's.
 */
@Composable
internal fun SystemKeyboardShortcutsEffect(
    viewModel: CampfireViewModel,
    onChanged: (List<KeyboardShortcutGroup>) -> Unit,
) {
    val groups = systemShortcutTexts(shortcutFeatures(viewModel.preferencesController))
    val latestOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(groups) {
        latestOnChanged(
            groups.map { group ->
                KeyboardShortcutGroup(
                    group.title,
                    group.shortcuts.map { KeyboardShortcutInfo(it.description, it.chord.key.nativeKeyCode, it.chord.metaState) },
                )
            },
        )
    }
}

/** The modifiers [KeyChord] is pressed with, as Android writes them: Ctrl for Ctrl / Cmd, and Shift where it is held. */
private val KeyChord.metaState
    get() = (if (isCommand) KeyEvent.META_CTRL_ON else 0) or
        (if (shift == KeyChord.Shift.REQUIRED || shift == KeyChord.Shift.IN_LABEL) KeyEvent.META_SHIFT_ON else 0)
