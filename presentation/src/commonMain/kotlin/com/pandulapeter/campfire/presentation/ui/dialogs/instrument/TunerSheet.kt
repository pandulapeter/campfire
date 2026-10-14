/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.instrument

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.platform.rememberMicrophonePermission
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.tuner.TunerAskOnOpenEffect
import com.pandulapeter.campfire.presentation.ui.tuner.TunerDisplay
import com.pandulapeter.campfire.presentation.ui.tuner.TunerListeningEffect
import com.pandulapeter.campfire.presentation.ui.tuner.TunerNoticeCard
import com.pandulapeter.campfire.presentation.ui.tuner.TunerStringsSetting
import com.pandulapeter.campfire.presentation.ui.tuner.TunerInputIssueMessage
import com.pandulapeter.campfire.presentation.ui.tuner.canListenWithoutTap
import com.pandulapeter.campfire.presentation.ui.tuner.tunerNoticeOf
import com.pandulapeter.campfire.tuner.api.model.TunerListening

/**
 * The tuner over the song being read, opened from the tuner button of the song's first section (or its overflow menu,
 * where the chords and the metronome are both switched off and that section is not there), so that a string that went
 * flat between two songs is tuned without leaving the setlist, laid out as the Tuner tab is: its display at full size
 * (compact only in a short window, as on the tab), giving way to its notice the same way, and under them the strings of
 * the instrument in the player's hands, all of it scrolling under the sheet's header. The instrument is the app's one,
 * set from the instrument chip of the Tuner tab, the Chords tab, Settings, the onboarding, or the chips at the top of the
 * song's first section, and the reference pitch and the chromatic switch are set on the tab alone, the way the metronome
 * panel plays with the Metronome tab's presets without offering them. It listens while it is up and the app is in front,
 * and the view model stops it as the sheet closes.
 */
@Composable
internal fun TunerSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.Tuner,
) {
    // The state changes with every reading, thirty times a second, so it is handed down as a State for the display to
    // read, and the rest of the page only reads what changes with a note or a setting.
    val state = viewModel.tunerController.tunerState.collectAsStateWithLifecycle()
    val config by viewModel.tunerController.tunerConfig.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val hasRequested by viewModel.tunerController.hasTurnedOnMicrophone.collectAsStateWithLifecycle()
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    val permission = rememberMicrophonePermission()
    val status = permission.status
    val notice by remember(status, hasRequested) {
        derivedStateOf { tunerNoticeOf(status = status, listening = state.value.listening, hasRequested = hasRequested) }
    }
    val isHearing by remember { derivedStateOf { state.value.listening is TunerListening.Hearing } }
    val tone by remember { derivedStateOf { state.value.tone } }
    val issue by remember { derivedStateOf { (state.value.listening as? TunerListening.Hearing)?.issue } }
    val heardNote by remember { derivedStateOf { (state.value.listening as? TunerListening.Hearing)?.reading?.note } }
    val tunedNotes by viewModel.tunerController.tunedNotes.collectAsStateWithLifecycle()
    TunerListeningEffect(
        canListen = canListenWithoutTap(status = permission.status, hasRequested = hasRequested),
        onListeningChanged = viewModel.tunerController::setTunerListening,
    )
    TunerAskOnOpenEffect(
        notice = notice,
        consumeOpenedByTap = viewModel.tunerController::consumeOpenedByTap,
        ask = permission.request ?: viewModel.tunerController::requestMicrophone,
    )
    CampfireBottomSheet(
        title = stringResource(Res.string.tuner),
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(bottom = 8.dp),
        ) {
            AnimatedVisibility(
                visible = notice == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                TunerDisplay(
                    state = state,
                    notation = notation,
                    referencePitch = config.referencePitch,
                    isCompact = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT,
                )
            }
            AnimatedSettingsRow(value = notice) { shownNotice ->
                TunerNoticeCard(
                    notice = shownNotice,
                    permission = permission,
                    onListen = viewModel.tunerController::requestMicrophone,
                )
            }
            TunerInputIssueMessage(issue = issue, permission = permission)
            TunerStringsSetting(
                config = config,
                notation = notation,
                tone = tone,
                heardNote = heardNote,
                tunedNotes = tunedNotes,
                onToggleTone = viewModel.tunerController::toggleTunerTone,
            )
        }
    }
}
