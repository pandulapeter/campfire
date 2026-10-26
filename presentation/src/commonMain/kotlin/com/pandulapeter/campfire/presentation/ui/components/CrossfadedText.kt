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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A line of a list item that a choice made on the screen rewrites, such as what a name already taken will become, so
 * that the new text crossfades in and the row grows or shrinks to it rather than jumping. Styled by the slot it is in.
 */
@Composable
internal fun CrossfadedText(
    modifier: Modifier = Modifier,
    text: String,
) = AnimatedContent(
    modifier = modifier,
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) {
    Text(text = it)
}
