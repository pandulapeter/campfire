/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.scaled

/**
 * The album, the year and the people behind the song, each a label over its value, flowing side by side where they fit:
 * as a table of two columns they left most of a wide sheet empty between a short label and a short value.
 */
@Composable
internal fun MetadataTiles(
    modifier: Modifier = Modifier,
    rows: List<Pair<String, String>>,
    fontScale: Float,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(24.dp * fontScale),
    verticalArrangement = Arrangement.spacedBy(12.dp * fontScale),
) {
    rows.forEach { (label, value) ->
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.scaled(fontScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.scaled(fontScale),
            )
        }
    }
}
