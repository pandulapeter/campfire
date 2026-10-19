/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What a set of [ScaleChip]s is for, a heading over them, [SCALE_CHIPS_TITLE_GAP] above the first row. */
@Composable
internal fun ScaleChipsTitle(
    modifier: Modifier = Modifier,
    title: String,
    textAlign: TextAlign,
) = Text(
    modifier = modifier.fillMaxWidth().semantics { heading() },
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = textAlign,
)

internal val SCALE_CHIPS_TITLE_GAP = 8.dp
