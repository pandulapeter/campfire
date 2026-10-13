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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_strings
import com.pandulapeter.campfire.presentation.resources.tuner_strings_description
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.tuner.api.model.TunerConfig

/**
 * The strings [config] listens for under their title ([TunerStrings]), the same on the tab and in the sheet over a song,
 * and nothing in chromatic mode, where there are no strings to play.
 */
@Composable
internal fun ColumnScope.TunerStringsSetting(
    config: TunerConfig,
    notation: ChordNotation,
    tone: Int?,
    heardNote: Int?,
    tunedNotes: Set<Int>,
    onToggleTone: (Int) -> Unit,
) {
    // Read through states, since the content of a row leaving is not sure to be recomposed with the newest parameters.
    val currentConfig by rememberUpdatedState(config)
    val currentTone by rememberUpdatedState(tone)
    // A row leaving with its instrument, or for chromatic mode, is drawn as it was but plays nothing: only a string the
    // tuner listens for now starts a tone, and the one sounding can always be stopped.
    val onToggleString: (Int) -> Unit = remember(onToggleTone) {
        { note -> if (note == currentTone || isStringOf(currentConfig, note)) onToggleTone(note) }
    }
    AnimatedSettingsRow(value = config.tuning?.let { it to config.partners }) { (shownTuning, partners) ->
        SettingsSubsection(
            title = stringResource(Res.string.tuner_strings),
            description = stringResource(Res.string.tuner_strings_description),
        ) {
            TunerStrings(
                tuning = shownTuning,
                partners = partners,
                notation = notation,
                tone = tone,
                heardNote = heardNote,
                tunedNotes = tunedNotes,
                onToggleTone = onToggleString,
            )
        }
    }
}
