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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_group_calibration
import com.pandulapeter.campfire.presentation.resources.tuner_group_tuning
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopOnRequest
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.rememberMicrophonePermission
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsPage
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
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
 * The Tuner tab: what is heard ([TunerDisplay]) pinned at the top, as wide as a page of two sections like the Metronome
 * tab's bar, and under it, scrolling, what the tuner is set to: the strings ([TunerStringsSetting]) first, since they are
 * what is reached for most while tuning, then the Chromatic switch and the instrument ([TunerInstrumentSetting]) and the
 * reference pitch ([ReferencePitchSetting]), offered here rather than on the sheet over a song, which tunes to what they
 * are set to.
 * The settings are two titled sections, as on every settings page: Tuning (the strings, Chromatic, and the instrument)
 * and Calibration (the reference pitch). Where the window has room for two columns the strings span both, untitled, as
 * the header of the page, and the two sections stand side by side under them; a narrower window stacks all of it in
 * that order in one column. While the
 * microphone cannot be listened to, the display gives way to a notice that is the page's first item ([TunerNoticeCard])
 * and scrolls with it, which costs a short window nothing; its tones still play then.
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
    val settings by viewModel.tunerController.tunerSettings.collectAsStateWithLifecycle()
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
    // A window with room for two columns gives the strings the whole width of the display above them, which they are
    // played against, and sets the instrument and the reference pitch side by side under them; a narrower one keeps
    // everything in one column, in the same order.
    val isWide = layout.sectionColumns >= 2
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
        AnimatedVisibility(
            visible = notice == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TunerDisplay(
                // As wide as the page under it, so that the meter ends where the rows it heads end.
                modifier = Modifier
                    .padding(top = DISPLAY_TOP_PADDING)
                    .padding(contentPadding.only(start = true, end = true))
                    .widthIn(
                        max = layout.pageMaxWidth(sections = 2) + contentPadding.calculateStartPadding(layoutDirection) +
                            contentPadding.calculateEndPadding(layoutDirection),
                    ),
                state = state,
                notation = notation,
                referencePitch = config.referencePitch,
                isCompact = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT,
            )
        }
        val strings: @Composable ColumnScope.() -> Unit = {
            TunerStringsSetting(
                config = config,
                notation = notation,
                tone = tone,
                heardNote = heardNote,
                tunedNotes = tunedNotes,
                onToggleTone = viewModel.tunerController::toggleTunerTone,
            )
        }
        SettingsPage(
            modifier = Modifier.weight(1f),
            sectionColumns = layout.sectionColumns,
            scrollState = scrollState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true),
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
                if (isWide) strings()
            },
            section = {
                SettingsSection(title = stringResource(Res.string.tuner_group_tuning)) {
                    if (!isWide) strings()
                    userPreferences?.let { preferences ->
                        TunerInstrumentSetting(
                            instrument = preferences.instrument,
                            tuning = preferences.instrumentTuning,
                            notation = notation,
                            isChromatic = settings.isChromatic,
                            onInstrumentClicked = { viewModel.dialogHost.showDialog(DialogType.Instrument) },
                            onTuningClicked = { viewModel.dialogHost.showDialog(DialogType.Tuning) },
                            onChromaticChanged = { value -> viewModel.tunerController.updateTunerSettings { copy(isChromatic = value) } },
                        )
                    }
                }
            },
            secondSection = {
                SettingsSection(title = stringResource(Res.string.tuner_group_calibration)) {
                    ReferencePitchSetting(
                        referencePitch = config.referencePitch,
                        notation = notation,
                        isReferenceTonePlaying = tone == REFERENCE_NOTE,
                        onReferencePitchChanged = { value -> viewModel.tunerController.updateTunerSettings { copy(referencePitch = value) } },
                        onToggleReferenceTone = { viewModel.tunerController.toggleTunerTone(REFERENCE_NOTE) },
                    )
                }
            },
        )
    }
}

/** What the display keeps free above itself, so that it sits where the settings screen's tabs do. */
private val DISPLAY_TOP_PADDING = 8.dp
