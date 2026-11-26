/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import org.jetbrains.compose.resources.painterResource

/**
 * The song details screen's way to the tuner sheet as an entry of its menu, never let out as a button: the bar's room is
 * the title's and the key chip's, and the Playing sheet, which the key chip opens, has the tuner beside the tuning too.
 */
@Composable
internal fun tunerAction(onClick: () -> Unit) = ActionsMenuItem(
    title = stringResource(Res.string.tuner),
    icon = painterResource(Res.drawable.ic_tuner),
    isAlwaysInMenu = true,
    key = TUNER_ACTION_KEY,
    shortcut = KeyShortcut.TUNER,
    onClick = onClick,
)

private const val TUNER_ACTION_KEY = "tuner"
