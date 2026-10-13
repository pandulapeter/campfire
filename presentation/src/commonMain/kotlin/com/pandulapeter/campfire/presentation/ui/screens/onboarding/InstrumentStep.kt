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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
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
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.components.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.instrument.hasDiagrams
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import org.jetbrains.compose.resources.painterResource

/**
 * "What do you play?", one instrument picked out of a grid, and then its tuning, which is the one instrument the chord
 * diagrams and the tuner are set up for ([UserPreferences.instrument]), Something else among them for every instrument
 * that is not there. Only asked while the chords or the tuner are on, and the line under the question names the one or
 * two of them the answer is for; with the tuner off only the instruments the diagrams are drawn for are offered
 * ([onboardingInstruments]). The instrument already in the preferences is the one shown picked, a fresh installation's
 * guitar included, so that going through the onboarding again starts from what the app is set to now.
 *
 * With the chords on, the switch for the diagrams is under the answers for an instrument they are drawn for, and while
 * it is on a G is drawn beside it in [chordTuning], so the answer shows what it will change on every song; an
 * instrument they are never drawn for leaves the place empty.
 */
@Composable
internal fun InstrumentStep(
    modifier: Modifier = Modifier,
    selected: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    chordTuning: ChordTuning?,
    notation: ChordNotation,
    areChordsEnabled: Boolean,
    isTunerEnabled: Boolean,
    areChordDiagramsEnabled: Boolean,
    isCompact: Boolean,
    onSelected: (UserPreferences.Instrument) -> Unit,
    onTuningSelected: (InstrumentTuning) -> Unit,
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
            title = onboardingInstrumentLabel(instrument),
            isSelected = instrument == selected,
            role = Role.RadioButton,
            onClick = { onSelected(instrument) },
        )
    }
    val tunedTuning = tuning?.takeIf { selected.tunings.size > 1 }
    AnimatedVisibility(
        visible = tunedTuning != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        // Kept while it shrinks away, so that the chips fade out as they were rather than as nothing.
        val shown = remember(tunedTuning) { tunedTuning?.let { selected to it } } ?: return@AnimatedVisibility
        OnboardingTuningChoice(
            instrument = shown.first,
            selected = shown.second,
            notation = notation,
            onSelected = onTuningSelected,
        )
    }
    if (areChordsEnabled) {
        AnimatedContent(
            targetState = selected.hasDiagrams,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            contentAlignment = Alignment.TopCenter,
        ) { hasDiagrams ->
            if (hasDiagrams) {
                ChordDiagramsSwitch(
                    tuning = chordTuning,
                    areChordDiagramsEnabled = areChordDiagramsEnabled,
                    onChordDiagramsEnabledChange = onChordDiagramsEnabledChange,
                )
            }
        }
    }
}

/**
 * The chord diagrams switch, and while it is on a G drawn beside it in [tuning]. The diagram sits beside its switch
 * rather than under it, where the answers above would push it past the bottom of a short window, so that picking an
 * instrument visibly redraws it.
 */
@Composable
private fun ChordDiagramsSwitch(
    tuning: ChordTuning?,
    areChordDiagramsEnabled: Boolean,
    onChordDiagramsEnabledChange: (Boolean) -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    SwitchListItem(
        modifier = Modifier.weight(1f),
        title = stringResource(Res.string.settings_chord_diagrams),
        description = stringResource(Res.string.settings_chord_diagrams_description),
        isChecked = areChordDiagramsEnabled,
        onCheckedChange = onChordDiagramsEnabledChange,
    )
    AnimatedVisibility(
        visible = areChordDiagramsEnabled && tuning != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        // Kept while it shrinks away, so that the diagram fades out as it was rather than as nothing.
        remember(tuning) { tuning }?.let { InstrumentChordPreview(tuning = it) }
    }
}

/** A G, as the song page draws it at the top of a song in [tuning]. */
@Composable
private fun InstrumentChordPreview(
    tuning: ChordTuning,
) = Box(
    modifier = Modifier.padding(end = 16.dp),
    contentAlignment = Alignment.Center,
) {
    val chord = remember { ChordProChords.parse(PREVIEW_CHORD) }
    val geometry = remember(chord, tuning) {
        chord?.let { ChordVoicings.default(it, tuning)?.let { shape -> chordDiagramGeometryOf(shape, tuning, it.root) } }
    }
    if (geometry != null) {
        ChordDiagram(
            modifier = if (tuning.instrument.isFretted) Modifier.size(width = 56.dp, height = 72.dp) else Modifier.size(width = 104.dp, height = 48.dp),
            geometry = geometry,
            description = stringResource(Res.string.onboarding_chord_diagram_description, PREVIEW_CHORD),
        )
    }
}

/** The page's own words for the two answers that are not an instrument's name, and the instrument's name otherwise. */
@Composable
private fun onboardingInstrumentLabel(instrument: UserPreferences.Instrument) = when (instrument) {
    UserPreferences.Instrument.KEYBOARD -> stringResource(Res.string.onboarding_instrument_keyboard)
    UserPreferences.Instrument.OTHER -> stringResource(Res.string.onboarding_instrument_other)
    else -> instrumentLabel(instrument)
}

/** The chord drawn for the instrument picked: one of the first a player learns, with a shape on every instrument. */
private const val PREVIEW_CHORD = "G"
