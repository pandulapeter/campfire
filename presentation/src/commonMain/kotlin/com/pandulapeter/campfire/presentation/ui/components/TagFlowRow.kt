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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The layout every group of tags is laid out in where it may take as many lines as it needs, which is everywhere but
 * a song list's rows ([SongLabels]). A tag is a word of whatever length its author chose, so they are flowed rather
 * than put in a grid whose columns would all be as wide as the longest one.
 *
 * @param gap Between two tags and between the lines they wrap onto, the same both ways so that they sit on an even
 *   grid: [TAG_GAP] for the small pills, [CHIP_GAP] for the filter chips.
 */
@Composable
internal fun TagFlowRow(
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    gap: Dp = TAG_GAP,
    content: @Composable FlowRowScope.() -> Unit,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(gap),
    verticalArrangement = Arrangement.spacedBy(gap),
    maxLines = maxLines,
    content = content,
)
