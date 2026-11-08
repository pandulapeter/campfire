/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * Whether the keyboard is up, derived so that a reader recomposes as it comes and goes rather than on every frame it
 * slides. The tools' buttons leave while it is: the room a small phone has above the keyboard is the search's and the
 * results', not a row of buttons that switch to another tool.
 */
@Composable
internal fun rememberIsKeyboardUp(): State<Boolean> {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
}
