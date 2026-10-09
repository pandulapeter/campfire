/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A paragraph inside a [SettingsSection] that belongs to the section rather than to one of its rows: what a section is
 * for, or what went wrong in it.
 *
 * @param isAnnounced Whether a screen reader reads the message out as it appears, for one that comes up on its own, in
 * answer to something the user did elsewhere or to nothing at all (a sound output that is missing, a connection that
 * failed), rather than being part of the section from the start.
 */
@Composable
internal fun SettingsMessage(
    modifier: Modifier = Modifier,
    text: String,
    isAnnounced: Boolean = false,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .then(if (isAnnounced) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
