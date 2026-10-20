/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.tuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopOnRequest
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.ToolInstrumentSetting
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.rememberMicrophonePermission
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsPage
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_GROUP_GAP
import com.pandulapeter.campfire.presentation.ui.settingsPage.PageWidthLayout
import com.pandulapeter.campfire.presentation.ui.tuner.REFERENCE_NOTE
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
 * The Tuner tab: the instrument ([ToolInstrumentSetting]) at the top, as on the Chords and the Scales tabs, with the
 * reference pitch ([ReferencePitchControls]) on its line, in the corner the list screens' pill and New button stand in, then what is
 * heard ([TunerDisplay]), all pinned, as wide as a page of two sections like the Metronome tab's bar, and under them,
 * scrolling, the strings ([TunerStringsSetting]), centered under the display they are played against. Other is the
 * instrument that reads every note. The instrument and the reference pitch are offered here rather than on the sheet
 * over a song, which tunes to what they are set to. While the microphone cannot be listened to, the display gives way
 * to a notice that is the page's first item ([TunerNoticeCard]) and scrolls with it, which costs a short window
 * nothing; its tones still play then.
 *
 * It listens while it is on screen and the app is in front ([TunerListeningEffect]) and nothing longer, keeping the
 * display on meanwhile, and asks for the microphone only as a tap opens it ([TunerAskOnOpenEffect]) or from the notice's
 * button.
 */
@Composable
internal fun TunerScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: PageWidthLayout,
    scrollPosition: ScrollPosition,
    contentPadding: PaddingValues,
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
    val layoutDirection = LocalLayoutDirection.current
    val scrollState = rememberRetainedScrollState(scrollPosition)
    ScrollToTopOnRequest(
        requests = viewModel.navigator.scrollToTopRequests,
        destination = CampfireDestination.Tuner,
        scrollState = scrollState,
        onSelectDestination = viewModel::selectTopLevelDestination,
    )
    Column(
        // A tuner is used with both hands on the instrument, and a screen that dims and locks stops the listening.
        modifier = modifier.fillMaxSize().then(if (isHearing) Modifier.keepScreenOn() else Modifier),
    ) {
        userPreferences?.let { preferences ->
            ToolInstrumentSetting(
                // At the top, as the list screens' bar is, so that the reference pitch stands on the New button's spot.
                modifier = Modifier.padding(contentPadding.only(start = true, end = true)),
                instrument = preferences.instrument,
                tuning = preferences.instrumentTuning,
                notation = notation,
                onInstrumentClicked = { viewModel.dialogHost.showDialog(DialogType.Instrument) },
                onTuningClicked = { viewModel.dialogHost.showDialog(DialogType.Tuning) },
            ) {
                ReferencePitchControls(
                    referencePitch = config.referencePitch,
                    notation = notation,
                    isReferenceTonePlaying = tone == REFERENCE_NOTE,
                    onReferencePitchChanged = { value -> viewModel.tunerController.updateTunerSettings { copy(referencePitch = value) } },
                    onToggleReferenceTone = { viewModel.tunerController.toggleTunerTone(REFERENCE_NOTE) },
                )
            }
        }
        AnimatedVisibility(
            visible = notice == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TunerDisplay(
                // As wide as the page under it, so that the meter ends where the rows it heads end.
                modifier = Modifier
                    .padding(top = TOOL_GROUP_GAP)
                    .padding(contentPadding.only(start = true, end = true))
                    .widthIn(
                        max = layout.animatedPageMaxWidth(sections = 2) + contentPadding.calculateStartPadding(layoutDirection) +
                            contentPadding.calculateEndPadding(layoutDirection),
                    ),
                state = state,
                notation = notation,
                referencePitch = config.referencePitch,
                isCompact = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT,
            )
        }
        SettingsPage(
            modifier = Modifier.weight(1f),
            sectionColumns = layout.sectionColumns,
            scrollState = scrollState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true),
            // As wide as the display above it, which the strings are centered under.
            headerColumns = 2,
            // What stands in for the display, or says why it hears nothing, stands above the sections as the display does.
            header = {
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
                    isTitled = false,
                    onToggleTone = viewModel.tunerController::toggleTunerTone,
                )
            },
        )
    }
}
