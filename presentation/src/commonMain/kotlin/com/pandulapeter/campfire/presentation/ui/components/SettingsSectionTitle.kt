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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Title of a group of controls: the label above a [SegmentedChoice] or a set of switches, in the same color as a
 * [SectionHeader] pill but without the pill, since it names a part of a section rather than a section.
 *
 * [contentPadding] defaults to the gaps a sheet or a side panel wants, where the groups are the whole content; a
 * list that has a rhythm of its own passes its own gaps instead.
 */
@Composable
internal fun SettingsSectionTitle(
    modifier: Modifier = Modifier,
    text: String,
    contentPadding: PaddingValues = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 24.dp, bottom = 8.dp),
) = Text(
    modifier = modifier.fillMaxWidth().padding(contentPadding),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
)
