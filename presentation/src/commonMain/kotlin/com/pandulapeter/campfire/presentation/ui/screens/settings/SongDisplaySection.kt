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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_tuning
import com.pandulapeter.campfire.presentation.resources.settings_chord_tuning_description
import com.pandulapeter.campfire.presentation.resources.settings_notation
import com.pandulapeter.campfire.presentation.resources.settings_notation_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_german
import com.pandulapeter.campfire.presentation.resources.settings_notation_german_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard_description
import com.pandulapeter.campfire.presentation.resources.settings_number_sections
import com.pandulapeter.campfire.presentation.resources.settings_number_sections_description
import com.pandulapeter.campfire.presentation.resources.settings_setlist_cards
import com.pandulapeter.campfire.presentation.resources.settings_setlist_cards_description
import com.pandulapeter.campfire.presentation.resources.settings_song_cards
import com.pandulapeter.campfire.presentation.resources.settings_song_cards_description
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.CardDetailsChoice
import com.pandulapeter.campfire.presentation.ui.components.ChordInstrumentList
import com.pandulapeter.campfire.presentation.ui.components.ChordTuningChips
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem

@Composable
internal fun SongDisplaySection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection {
    SwitchListItem(
        title = stringResource(Res.string.settings_number_sections),
        description = stringResource(Res.string.settings_number_sections_description),
        isChecked = userPreferences?.shouldNumberSections == true,
        onCheckedChange = viewModel::setSectionNumberingEnabled,
    )
    // One row of chips per list, since a setlist's cards are where a set is timed and the library's where a song is found.
    // The key's chip is gone with the chords, which take it off the cards whatever it says; the tempo's stays with the
    // metronome off, which only decides it until it is picked. The song details screen is not theirs: the top of a song
    // is where its key and tempo are changed.
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    val isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false
    SettingsSubsection(
        title = stringResource(Res.string.settings_song_cards),
        description = stringResource(Res.string.settings_song_cards_description),
    ) {
        CardDetailsChoice(
            details = userPreferences?.songCardDetails ?: UserPreferences.CardDetails.SongsDefault,
            areChordsEnabled = areChordsEnabled,
            isMetronomeEnabled = isMetronomeEnabled,
            onDetailsChanged = viewModel::setSongCardDetails,
        )
    }
    val areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false
    SettingsSubsection(
        title = stringResource(Res.string.settings_setlist_cards),
        description = stringResource(Res.string.settings_setlist_cards_description),
        isEnabled = areSetlistsEnabled,
    ) {
        CardDetailsChoice(
            details = userPreferences?.setlistCardDetails ?: UserPreferences.CardDetails.SetlistsDefault,
            areChordsEnabled = areChordsEnabled,
            isMetronomeEnabled = isMetronomeEnabled,
            isEnabled = areSetlistsEnabled,
            onDetailsChanged = viewModel::setSetlistCardDetails,
        )
    }
    // Both of these only decide how a chord is written, so with the chords switched off they have nothing to say. They
    // stay in the section rather than disappearing from it: what they are set to is still what the chords will look
    // like as soon as they are shown again. The accidentals stay enabled under a numbering, since they still spell the
    // key it counts from.
    val isChordSpellingEnabled = areChordsEnabled
    // The examples under the settings below are written in the notation picked here, so they change with it.
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    NotationChoice(
        selected = userPreferences?.chordSpelling?.notation,
        isEnabled = isChordSpellingEnabled,
        onSelected = viewModel::setNotation,
    )
    SettingsSubsection(
        title = stringResource(Res.string.settings_accidentals),
        description = stringResource(
            Res.string.settings_accidentals_description,
            ChordProNotation.shownName("Bb", notation),
            ChordProNotation.shownName("A#", notation),
        ),
        isEnabled = isChordSpellingEnabled,
    ) {
        SegmentedChoice(
            options = listOf(
                UserPreferences.Accidentals.ORIGINAL to stringResource(Res.string.settings_accidentals_original),
                UserPreferences.Accidentals.FLATS to stringResource(Res.string.settings_accidentals_flats),
                UserPreferences.Accidentals.SHARPS to stringResource(Res.string.settings_accidentals_sharps),
            ),
            selected = userPreferences?.chordSpelling?.accidentals,
            isEnabled = isChordSpellingEnabled,
            onSelected = viewModel::setAccidentals,
        )
    }
    ChordInstrumentChoice(
        selected = userPreferences?.chordInstrument,
        chordTunings = userPreferences?.chordTunings.orEmpty(),
        notation = notation,
        // Enabled with the diagrams switched off too, since the keyboard also reads the capo as a transposition.
        isEnabled = isChordSpellingEnabled,
        onSelected = viewModel::setChordInstrument,
    )
    // The tuning only decides the diagrams, so unlike the instrument it goes with them.
    AnimatedSettingsRow(value = userPreferences?.chordTuning()?.takeIf { ChordTuning.of(it.instrument).size > 1 }) { tuning ->
        val isTuningEnabled = isChordSpellingEnabled && userPreferences?.areChordDiagramsEnabled != false
        SettingsSubsection(
            title = stringResource(Res.string.settings_chord_tuning),
            description = stringResource(Res.string.settings_chord_tuning_description),
            isEnabled = isTuningEnabled,
        ) {
            ChordTuningChips(
                selected = tuning,
                notation = notation,
                isEnabled = isTuningEnabled,
                onSelected = viewModel::setChordTuning,
            )
        }
    }
}

/**
 * The instrument the chord diagrams are drawn for, and on the keyboard what reads the capo as a transposition, see
 * [ChordInstrumentList].
 */
@Composable
private fun ChordInstrumentChoice(
    selected: UserPreferences.ChordInstrument?,
    chordTunings: Map<String, String>,
    notation: ChordNotation,
    isEnabled: Boolean,
    onSelected: (UserPreferences.ChordInstrument) -> Unit,
) = SettingsSubsection(
    title = stringResource(Res.string.settings_chord_instrument),
    description = stringResource(Res.string.settings_chord_instrument_description),
    isEnabled = isEnabled,
) {
    ChordInstrumentList(
        selected = selected,
        chordTunings = chordTunings,
        notation = notation,
        isEnabled = isEnabled,
        onSelected = onSelected,
    )
}

/**
 * The notation chords are written in, as a list rather than a segmented control: five names with an example under each
 * do not fit a row of segments at the width of a phone. Each example is what tells the options apart to somebody who
 * has only ever read one of them.
 */
@Composable
private fun NotationChoice(
    selected: UserPreferences.Notation?,
    isEnabled: Boolean,
    onSelected: (UserPreferences.Notation) -> Unit,
) = SettingsSubsection(
    title = stringResource(Res.string.settings_notation),
    description = stringResource(Res.string.settings_notation_description),
    isEnabled = isEnabled,
) {
    Column(modifier = Modifier.selectableGroup()) {
        listOf(
            Triple(UserPreferences.Notation.STANDARD, Res.string.settings_notation_standard, Res.string.settings_notation_standard_description),
            Triple(UserPreferences.Notation.GERMAN, Res.string.settings_notation_german, Res.string.settings_notation_german_description),
            Triple(UserPreferences.Notation.LATIN, Res.string.settings_notation_latin, Res.string.settings_notation_latin_description),
            Triple(UserPreferences.Notation.NASHVILLE, Res.string.settings_notation_nashville, Res.string.settings_notation_nashville_description),
            Triple(UserPreferences.Notation.ROMAN, Res.string.settings_notation_roman, Res.string.settings_notation_roman_description),
        ).forEach { (notation, title, description) ->
            RadioListItem(
                title = stringResource(title),
                description = stringResource(description),
                isSelected = selected == notation,
                isEnabled = isEnabled,
                onSelected = { onSelected(notation) },
            )
        }
    }
}
