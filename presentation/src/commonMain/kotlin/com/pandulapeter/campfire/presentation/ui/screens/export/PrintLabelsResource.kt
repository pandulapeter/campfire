/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.resources.print_time
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.print.PrintLabels
import com.pandulapeter.campfire.presentation.ui.print.TEMPO_VALUE
import com.pandulapeter.campfire.presentation.ui.songLayout.rememberDefaultSectionLabels

/**
 * The words the PDF prints its values under, in the app's language, and the line naming the instrument and the tuning
 * its chord diagrams are drawn for, which says nothing for the instruments the diagrams name themselves.
 */
@Composable
internal fun rememberPrintLabels(preferences: UserPreferences?): PrintLabels {
    val key = stringResource(Res.string.song_editor_insert_key)
    val transposition = stringResource(Res.string.song_details_transposition)
    val capo = stringResource(Res.string.song_editor_insert_capo)
    val tempo = stringResource(Res.string.song_editor_insert_tempo)
    val tempoValue = textResource(Res.string.song_details_tempo, TEMPO_VALUE)
    val time = stringResource(Res.string.print_time)
    val missing = stringResource(Res.string.print_missing)
    val sections = rememberDefaultSectionLabels(shouldNumberSections = preferences?.shouldNumberSections == true)
    val chordTuning = preferences?.let { preferences ->
        preferences.chordTuning()?.let { tuning ->
            listOfNotNull(
                instrumentLabel(preferences.instrument).takeIf { preferences.instrument !in INSTRUMENTS_NAMED_BY_DIAGRAMS },
                if (tuning.isStandard) {
                    null
                } else {
                    stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(tuning, preferences.chordSpelling.notation.toChordNotation()))
                },
            ).joinToString(" · ").ifEmpty { null }
        }
    }
    return remember(key, transposition, capo, tempo, tempoValue, time, missing, sections, chordTuning) {
        PrintLabels(
            key = key,
            transposition = transposition,
            capo = capo,
            tempo = tempo,
            tempoValue = tempoValue,
            time = time,
            missing = missing,
            sections = sections,
            chordTuning = chordTuning,
        )
    }
}

/**
 * The instruments the PDF's tuning line leaves unnamed: the guitar's diagrams are what a reader expects, the twelve-string
 * is drawn as the guitar, and the keyboard's diagram is a keyboard.
 */
private val INSTRUMENTS_NAMED_BY_DIAGRAMS = setOf(
    UserPreferences.Instrument.GUITAR,
    UserPreferences.Instrument.GUITAR_TWELVE_STRING,
    UserPreferences.Instrument.KEYBOARD,
)
