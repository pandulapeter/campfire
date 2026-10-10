/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.platform.rememberMicrophonePermission
import com.pandulapeter.campfire.presentation.ui.tuner.TunerDisplay
import com.pandulapeter.campfire.presentation.ui.tuner.TunerListeningEffect
import com.pandulapeter.campfire.presentation.ui.tuner.TunerNoticeCard
import com.pandulapeter.campfire.presentation.ui.tuner.TunerOptions
import com.pandulapeter.campfire.presentation.ui.tuner.canListenWithoutTap
import com.pandulapeter.campfire.presentation.ui.tuner.toConfig
import com.pandulapeter.campfire.presentation.ui.tuner.tunerNoticeOf
import com.pandulapeter.campfire.tuner.api.model.TunerListening

/**
 * The tuner over the song being read, opened from the song details overflow menu, so that a string that went flat
 * between two songs is tuned without leaving the setlist: the Tuner tab's own display (or its notice) and options, all
 * of it scrolling under the sheet's header, the display first since it is what the sheet is opened for. It listens
 * while it is up and the app is in front, and the view model stops it as the sheet closes.
 */
@Composable
internal fun TunerSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.Tuner,
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
    val config = remember(settings) { settings.toConfig() }
    TunerListeningEffect(
        canListen = canListenWithoutTap(status = permission.status, hasRequested = hasRequested),
        onListeningChanged = viewModel::setTunerListening,
    )
    CampfireBottomSheet(
        title = stringResource(Res.string.tuner),
        onDismiss = { viewModel.dismissSheet(dialog) },
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
            AnimatedContent(
                targetState = notice,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                label = "tunerSheetHeader",
            ) { shownNotice ->
                if (shownNotice == null) {
                    TunerDisplay(
                        state = state,
                        notation = notation,
                        referencePitch = config.referencePitch,
                        isCompact = true,
                    )
                } else {
                    TunerNoticeCard(
                        notice = shownNotice,
                        permission = permission,
                        onListen = viewModel::requestMicrophone,
                    )
                }
            }
            TunerOptions(
                tone = tone,
                issue = issue,
                heardNote = heardNote,
                config = config,
                notation = notation,
                permission = permission,
                onSettingsChanged = viewModel::updateTunerSettings,
                onToggleTone = viewModel::toggleTunerTone,
            )
        }
    }
}
