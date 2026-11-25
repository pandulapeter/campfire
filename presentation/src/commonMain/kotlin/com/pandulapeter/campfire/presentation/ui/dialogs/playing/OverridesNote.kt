/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.playing

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A quiet line at the end of the Playing and Tempo sheets saying where what they change is kept (a setlist's entry or the
 * app's preferences), and that the song file is never written: nothing in either sheet saves, so without it the steppers
 * read as editing the song. It scrolls with the steppers rather than being pinned, so it costs nothing of the sheet's
 * pinned height.
 */
@Composable
internal fun OverridesNote(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier.fillMaxWidth(),
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
