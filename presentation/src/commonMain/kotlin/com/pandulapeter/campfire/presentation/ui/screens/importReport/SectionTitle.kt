/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * A heading of the list, in the color the list screens' section headers are drawn in, its count written into it. The
 * count changes under a search and as a batch joins the review, crossfading.
 */
@Composable
internal fun SectionTitle(
    modifier: Modifier = Modifier,
    text: String,
) = AnimatedContent(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) {
    Text(
        modifier = Modifier.semantics { heading() },
        text = it,
        style = MaterialTheme.typography.titleSmall,
        color = LocalSecondAccentColor.current,
    )
}
