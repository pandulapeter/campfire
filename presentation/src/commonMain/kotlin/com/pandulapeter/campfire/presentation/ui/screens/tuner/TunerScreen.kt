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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.rememberMicrophonePermission
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsPage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSection
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout
import com.pandulapeter.campfire.presentation.ui.tuner.TunerDisplay
import com.pandulapeter.campfire.presentation.ui.tuner.TunerListeningEffect
import com.pandulapeter.campfire.presentation.ui.tuner.TunerNoticeCard
import com.pandulapeter.campfire.presentation.ui.tuner.TunerOptions
import com.pandulapeter.campfire.presentation.ui.tuner.canListenWithoutTap
import com.pandulapeter.campfire.presentation.ui.tuner.toConfig
import com.pandulapeter.campfire.presentation.ui.tuner.tunerNoticeOf
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import kotlinx.coroutines.flow.collectLatest

/**
 * The Tuner tab: what is heard ([TunerDisplay]) pinned at the top, capped at the page's width, and under it, scrolling,
 * what the tuner is set to ([TunerOptions]). While the microphone cannot be listened to, the display gives way to a
 * notice that is the page's first item ([TunerNoticeCard]) and scrolls with it, which costs a short window nothing; its
 * tones still play then.
 *
 * It listens while it is on screen and the app is in front ([TunerListeningEffect]) and nothing longer, keeping the
 * display on meanwhile, and asks for the microphone only from the notice's button.
 */
@Composable
internal fun TunerScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: SettingsWidthLayout,
    scrollPosition: ScrollPosition,
    contentPadding: PaddingValues,
) {
    // The state changes with every reading, thirty times a second, so it is handed down as a State for the display to
    // read, and the rest of the page only reads what changes with a note or a setting.
    val state = viewModel.tunerState.collectAsStateWithLifecycle()
    val settings by viewModel.tunerSettings.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val hasRequested by viewModel.hasTurnedOnMicrophone.collectAsStateWithLifecycle()
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
    val tunedNotes by viewModel.tunedNotes.collectAsStateWithLifecycle()
    val config = remember(settings) { settings.toConfig() }
    TunerListeningEffect(
        canListen = canListenWithoutTap(status = permission.status, hasRequested = hasRequested),
        onListeningChanged = viewModel::setTunerListening,
    )
    val layoutDirection = LocalLayoutDirection.current
    val scrollState = rememberRetainedScrollState(scrollPosition)
    LaunchedEffect(viewModel, scrollState) {
        // Latest, for the reason the settings screen gives.
        viewModel.scrollToTopRequests.collectLatest { if (it == CampfireDestination.Tuner) scrollState.animateScrollTo(0) }
    }
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
                        max = layout.pageMaxWidth(sections = 1) + contentPadding.calculateStartPadding(layoutDirection) +
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
            section = {
                SettingsSection {
                    AnimatedSettingsRow(value = notice) { shownNotice ->
                        TunerNoticeCard(
                            notice = shownNotice,
                            permission = permission,
                            onListen = viewModel::requestMicrophone,
                        )
                    }
                    TunerOptions(
                        tone = tone,
                        issue = issue,
                        heardNote = heardNote,
                        tunedNotes = tunedNotes,
                        config = config,
                        notation = notation,
                        permission = permission,
                        onSettingsChanged = viewModel::updateTunerSettings,
                        onToggleTone = viewModel::toggleTunerTone,
                    )
                }
            },
        )
    }
}

/** What the display keeps free above itself, so that it sits where the settings screen's tabs do. */
private val DISPLAY_TOP_PADDING = 8.dp
