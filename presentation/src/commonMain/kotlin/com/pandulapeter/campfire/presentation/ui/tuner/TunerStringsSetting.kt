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
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_strings
import com.pandulapeter.campfire.presentation.resources.tuner_strings_description
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The strings of [tuning] under their title ([TunerStrings]), the same on the tab and in the sheet over a song, and
 * nothing in chromatic mode, where there are no strings to play.
 */
@Composable
internal fun ColumnScope.TunerStringsSetting(
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    tone: Int?,
    heardNote: Int?,
    tunedNotes: Set<Int>,
    onToggleTone: (Int) -> Unit,
) = AnimatedSettingsRow(value = tuning) { shownTuning ->
    SettingsSubsection(
        title = stringResource(Res.string.tuner_strings),
        description = stringResource(Res.string.tuner_strings_description),
    ) {
        TunerStrings(
            tuning = shownTuning,
            notation = notation,
            tone = tone,
            heardNote = heardNote,
            tunedNotes = tunedNotes,
            onToggleTone = onToggleTone,
        )
    }
}
