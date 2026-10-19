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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP

/**
 * Scales suggested for something to play over, under [title] where there is one: a [ScaleChip] per scale, centered under
 * a chord's sheet, whose grid is centered. A song's page lays the same chips out in rows of its own (`ScaleChipRows`),
 * which its columns can be cut between.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScaleChips(
    modifier: Modifier = Modifier,
    title: String?,
    scales: List<Scale>,
    rootName: String,
    notation: ChordNotation,
    onScaleSelected: (scaleId: String, rootName: String) -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth(),
) {
    if (title != null) {
        ScaleChipsTitle(
            modifier = Modifier.padding(bottom = SCALE_CHIPS_TITLE_GAP),
            title = title,
            textAlign = TextAlign.Center,
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP, alignment = Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        scales.forEach { scale ->
            ScaleChip(
                scale = scale,
                rootName = rootName,
                notation = notation,
                onScaleSelected = onScaleSelected,
            )
        }
    }
}
