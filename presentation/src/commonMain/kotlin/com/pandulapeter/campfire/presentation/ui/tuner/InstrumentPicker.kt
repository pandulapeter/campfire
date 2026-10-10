/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_chromatic_description
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What the tuner's instrument sheet holds: a row of segments ([InstrumentFamilySegments]) kept above the rest, since it
 * switches the whole of what is under it, and under it, scrolling, the chips of the family shown, like the Metronome
 * tab's time signatures: under a heading per instrument where the family has more than one (a six-, seven- and
 * eight-string guitar), so that a chip names only the tuning, and is read with its instrument ("4-string bass, Drop D").
 * A segment only shows its family, which opens on the selected tuning's; a chip is the choice ([onSelected]). Chromatic
 * is a family of one chip, under a sentence saying what it reads.
 */
@Composable
internal fun ColumnScope.InstrumentPicker(
    selected: InstrumentTuning?,
    contentPadding: PaddingValues,
    fadeColor: Color,
    onSelected: (InstrumentTuning?) -> Unit,
) {
    var shownFamily by remember { mutableStateOf(familyOf(selected)) }
    InstrumentFamilySegments(
        modifier = Modifier.padding(bottom = 8.dp),
        selected = shownFamily,
        onSelected = { shownFamily = it },
    )
    val scrollState = rememberScrollState()
    AnimatedContent(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .fadingTopEdge(scrollState, fadeColor)
            .bounceVerticalScroll(scrollState)
            .padding(contentPadding)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        targetState = shownFamily,
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        contentAlignment = Alignment.TopStart,
        label = "instrumentFamilyTunings",
    ) { family ->
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            if (family == null) {
                Text(
                    modifier = Modifier.padding(bottom = 12.dp),
                    text = stringResource(Res.string.tuner_chromatic_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TuningChips(
                    tunings = listOf(null),
                    selected = selected,
                    onSelected = onSelected,
                )
            } else {
                family.groups.forEachIndexed { index, group ->
                    if (family.groups.size > 1) {
                        Text(
                            modifier = Modifier
                                .padding(top = if (index == 0) 0.dp else GROUP_TOP_GAP, bottom = CHIP_GAP)
                                .semantics { heading() },
                            text = groupLabel(group),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TuningChips(
                        tunings = group.tunings,
                        selected = selected,
                        onSelected = onSelected,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuningChips(
    tunings: List<InstrumentTuning?>,
    selected: InstrumentTuning?,
    onSelected: (InstrumentTuning?) -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    tunings.forEach { tuning ->
        val description = instrumentLabel(tuning)
        SelectableChip(
            isSelected = tuning == selected,
            role = Role.RadioButton,
            onClick = { onSelected(tuning) },
        ) { contentColor ->
            Text(
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                text = if (tuning == null) description else tuningLabel(tuning),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

/**
 * What keeps an instrument's name clear of the chips of the one above it: more than the [CHIP_GAP] between two rows of
 * them, so that the name reads as the start of the next instrument rather than as one more row of the last.
 */
private val GROUP_TOP_GAP = 16.dp
