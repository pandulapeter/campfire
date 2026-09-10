/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shown under the app bar of the screens an import can be started from, for as long as one is running. A big archive
 * on the web takes a while, and the screens it lands on look unchanged until it is done.
 */
@Composable
internal fun ImportProgress(
    modifier: Modifier = Modifier,
    isImporting: Boolean
) = AnimatedVisibility(
    modifier = modifier,
    visible = isImporting,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically()
) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}
