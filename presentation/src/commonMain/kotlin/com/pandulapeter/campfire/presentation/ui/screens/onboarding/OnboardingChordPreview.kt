/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_chord_diagram_description
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * A few of the first chords a player learns, each named over its diagram on a card as the Chords tab draws them, in
 * [tuning]: what the instrument page's answer changes on every song. Three, since three keyboard diagrams still fit
 * beside each other on the smallest phone.
 */
@Composable
internal fun OnboardingChordPreview(
    modifier: Modifier = Modifier,
    tuning: ChordTuning,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
) {
    PREVIEW_CHORDS.forEach { name ->
        val geometry = rememberChordGeometry(name = name, tuning = tuning) ?: return@forEach
        val description = stringResource(Res.string.onboarding_chord_diagram_description, name)
        Surface(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalSecondAccentColor.current,
                )
                ChordDiagram(
                    modifier = if (tuning.instrument.isFretted) Modifier.size(width = 64.dp, height = 80.dp) else Modifier.size(width = 84.dp, height = 38.dp),
                    geometry = geometry,
                    description = description,
                )
            }
        }
    }
}

/** The diagram of the chord called [name] in [tuning], with the shape a song draws it with first, null where it has none. */
@Composable
private fun rememberChordGeometry(name: String, tuning: ChordTuning): ChordDiagramGeometry? = remember(name, tuning) {
    ChordProChords.parse(name)?.let { chord ->
        ChordVoicings.default(chord, tuning)?.let { shape -> chordDiagramGeometryOf(shape, tuning, chord.root) }
    }
}

private val PREVIEW_CHORDS = listOf("G", "C", "D")
