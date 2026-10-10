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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.data.model.domain.TunerSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_instrument
import com.pandulapeter.campfire.presentation.resources.tuner_notice_silent
import com.pandulapeter.campfire.presentation.resources.tuner_notice_waiting
import com.pandulapeter.campfire.presentation.resources.tuner_open_settings
import com.pandulapeter.campfire.presentation.resources.tuner_strings
import com.pandulapeter.campfire.presentation.resources.tuner_strings_description
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.platform.MicrophonePermission
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsMessage
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerInputIssue

/**
 * What the tuner is set to, the same on the tab and in the sheet over a song: what keeps an open microphone from
 * hearing anything where that is so, the instrument, its strings to play (none in chromatic mode) and the reference
 * pitch. Nothing here writes a file, so read only mode leaves all of it in place.
 */
@Composable
internal fun TunerOptions(
    modifier: Modifier = Modifier,
    tone: Int?,
    issue: TunerInputIssue?,
    heardNote: Int?,
    tunedNotes: Set<Int>,
    config: TunerConfig,
    notation: ChordNotation,
    permission: MicrophonePermission,
    onSettingsChanged: (TunerSettings.() -> TunerSettings) -> Unit,
    onToggleTone: (Int) -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    AnimatedSettingsRow(value = issue) { shownIssue ->
        Column {
            SettingsMessage(
                text = stringResource(
                    when (shownIssue) {
                        TunerInputIssue.SILENT -> Res.string.tuner_notice_silent
                        TunerInputIssue.WAITING_FOR_GESTURE -> Res.string.tuner_notice_waiting
                    }
                ),
                isAnnounced = true,
            )
            val openSettings = permission.openSettings
            if (shownIssue == TunerInputIssue.SILENT && openSettings != null) {
                TextButton(modifier = Modifier.padding(horizontal = 4.dp), onClick = openSettings) {
                    Text(stringResource(Res.string.tuner_open_settings))
                }
            }
        }
    }
    SettingsSubsection(title = stringResource(Res.string.tuner_instrument)) {
        InstrumentChoice(
            selected = config.tuning,
            onSelected = { tuning -> onSettingsChanged { copy(instrumentId = tuning?.id ?: InstrumentTuning.CHROMATIC_ID) } },
        )
    }
    AnimatedSettingsRow(value = config.tuning) { tuning ->
        SettingsSubsection(
            title = stringResource(Res.string.tuner_strings),
            description = stringResource(Res.string.tuner_strings_description),
        ) {
            TunerStrings(
                tuning = tuning,
                notation = notation,
                tone = tone,
                heardNote = heardNote,
                tunedNotes = tunedNotes,
                onToggleTone = onToggleTone,
            )
        }
    }
    ReferencePitchSetting(
        referencePitch = config.referencePitch,
        notation = notation,
        isReferenceTonePlaying = tone == REFERENCE_NOTE,
        onReferencePitchChanged = { value -> onSettingsChanged { copy(referencePitch = value) } },
        onToggleReferenceTone = { onToggleTone(REFERENCE_NOTE) },
    )
}
