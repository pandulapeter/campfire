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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.onboarding_chord_diagram_description
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_keyboard
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_message_chords
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_message_chords_and_tuner
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_message_tuner
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_other
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_title
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_banjo
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_bass
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_violin
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.components.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import org.jetbrains.compose.resources.painterResource

/**
 * "What do you play?", one instrument picked out of a grid, which sets the chord diagrams and the tuner for it at once
 * (see [OnboardingInstrument]), Something else among them for every instrument that is not there. Only asked while the
 * chords or the tuner are on, and the line under the question names the one or two of them the answer is for; with the
 * tuner off only the instruments the diagrams are drawn for are offered ([onboardingInstruments]).
 *
 * With the chords on, the switch for the diagrams is under the answers, and while it is on a G is drawn beside it for the
 * instrument picked, so the answer shows what it will change on every song.
 */
@Composable
internal fun InstrumentStep(
    modifier: Modifier = Modifier,
    selected: OnboardingInstrument?,
    areChordsEnabled: Boolean,
    isTunerEnabled: Boolean,
    areChordDiagramsEnabled: Boolean,
    isCompact: Boolean,
    onSelected: (OnboardingInstrument) -> Unit,
    onChordDiagramsEnabledChange: (Boolean) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_tuner),
    title = stringResource(Res.string.onboarding_instrument_title),
    message = stringResource(
        when {
            areChordsEnabled && isTunerEnabled -> Res.string.onboarding_instrument_message_chords_and_tuner
            areChordsEnabled -> Res.string.onboarding_instrument_message_chords
            else -> Res.string.onboarding_instrument_message_tuner
        }
    ),
    isCompact = isCompact,
) {
    val instruments = onboardingInstruments(isTunerEnabled = isTunerEnabled)
    OnboardingChoiceGrid(
        modifier = Modifier.selectableGroup(),
        columns = 2,
        count = instruments.size,
    ) { index, itemModifier ->
        val instrument = instruments[index]
        OnboardingChoiceCard(
            modifier = itemModifier,
            title = instrument.label(),
            isSelected = instrument == selected,
            role = Role.RadioButton,
            onClick = { onSelected(instrument) },
        )
    }
    if (areChordsEnabled) {
        // The diagram sits beside its switch rather than under it, where the answers above would push it past the bottom
        // of a short window, so that picking an instrument visibly redraws it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwitchListItem(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.settings_chord_diagrams),
                description = stringResource(Res.string.settings_chord_diagrams_description),
                isChecked = areChordDiagramsEnabled,
                onCheckedChange = onChordDiagramsEnabledChange,
            )
            val diagramInstrument = selected?.chordInstrument?.toChordInstrument()
            AnimatedVisibility(
                visible = areChordDiagramsEnabled && diagramInstrument != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                // Kept while it shrinks away, so that the diagram fades out as it was rather than as nothing.
                remember(diagramInstrument) { diagramInstrument }?.let { InstrumentChordPreview(instrument = it) }
            }
        }
    }
}

/** A G, as the song page draws it at the top of a song for [instrument]. */
@Composable
private fun InstrumentChordPreview(
    instrument: ChordInstrument,
) = Box(
    modifier = Modifier.padding(end = 16.dp),
    contentAlignment = Alignment.Center,
) {
    val chord = remember { ChordProChords.parse(PREVIEW_CHORD) }
    val geometry = remember(chord, instrument) {
        chord?.let { ChordVoicings.default(it, instrument)?.let { shape -> chordDiagramGeometryOf(shape, instrument, it.root) } }
    }
    if (geometry != null) {
        ChordDiagram(
            modifier = if (instrument.isFretted) Modifier.size(width = 56.dp, height = 72.dp) else Modifier.size(width = 104.dp, height = 48.dp),
            geometry = geometry,
            description = stringResource(Res.string.onboarding_chord_diagram_description, PREVIEW_CHORD),
        )
    }
}

@Composable
private fun OnboardingInstrument.label() = stringResource(
    when (this) {
        OnboardingInstrument.GUITAR -> Res.string.tuner_instrument_guitar
        OnboardingInstrument.UKULELE -> Res.string.tuner_instrument_ukulele
        OnboardingInstrument.KEYBOARD -> Res.string.onboarding_instrument_keyboard
        OnboardingInstrument.BASS -> Res.string.tuner_instrument_bass
        OnboardingInstrument.BANJO -> Res.string.tuner_instrument_banjo
        OnboardingInstrument.MANDOLIN -> Res.string.tuner_instrument_mandolin
        OnboardingInstrument.VIOLIN -> Res.string.tuner_instrument_violin
        OnboardingInstrument.OTHER -> Res.string.onboarding_instrument_other
    }
)

/** The chord drawn for the instrument picked: one of the first a player learns, with a shape on every instrument. */
private const val PREVIEW_CHORD = "G"
