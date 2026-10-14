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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_notice_silent
import com.pandulapeter.campfire.presentation.resources.tuner_notice_silent_enhancements
import com.pandulapeter.campfire.presentation.resources.tuner_notice_waiting
import com.pandulapeter.campfire.presentation.resources.tuner_open_settings
import com.pandulapeter.campfire.presentation.resources.tuner_open_sound_settings
import com.pandulapeter.campfire.presentation.ui.platform.MicrophonePermission
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsMessage
import com.pandulapeter.campfire.tuner.api.model.TunerInputIssue

/**
 * What keeps an open microphone from hearing anything, where that is so, the same on the tab and in the sheet over a
 * song: a polite live region, since it arrives without a tap, with Open settings where silence may be a refusal.
 */
@Composable
internal fun ColumnScope.TunerInputIssueMessage(
    issue: TunerInputIssue?,
    permission: MicrophonePermission,
) = AnimatedSettingsRow(value = issue) { shownIssue ->
    val openSoundSettings = permission.openSoundSettings
    Column {
        SettingsMessage(
            text = stringResource(
                when (shownIssue) {
                    TunerInputIssue.SILENT -> if (openSoundSettings != null) Res.string.tuner_notice_silent_enhancements else Res.string.tuner_notice_silent
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
        if (shownIssue == TunerInputIssue.SILENT && openSoundSettings != null) {
            TextButton(modifier = Modifier.padding(horizontal = 4.dp), onClick = openSoundSettings) {
                Text(stringResource(Res.string.tuner_open_sound_settings))
            }
        }
    }
}
