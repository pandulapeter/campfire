/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.chrome.SharedToolsButtonGroup
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalIsCoveredByRequiredUpdate
import com.pandulapeter.campfire.presentation.ui.messages.Messages
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportHost
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportTransition

/**
 * What [CampfireScreens] draws over the screens: the shared tools' buttons, the export screen, the dialogs, and the
 * messages.
 */
@Composable
internal fun BoxScope.CampfireOverlays(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
    chromeInScreens: Boolean,
    groupedTools: List<CampfireDestination.TopLevel>,
    railWidth: () -> Dp,
    shellContentPadding: PaddingValues,
    metronomeBeat: MetronomeIconBeat,
    isKeyboard: Boolean,
    exportTransition: ExportTransition,
    messagesPadding: PaddingValues,
) {
    // Not while the screens draw a chrome of their own, each tool screen a row of its own with it (ToolScreenFrame).
    // Composed while the tools have items of their own too, so that it fades in and out as a resized window groups
    // them and lets them go.
    if (!chromeInScreens) {
        SharedToolsButtonGroup(
            backStack = viewModel.navigator.backStack,
            tools = groupedTools,
            destinations = viewModel.preferencesController.topLevelDestinations.collectAsStateWithLifecycle().value,
            railWidth = railWidth,
            contentPadding = shellContentPadding,
            metronomeBeat = metronomeBeat,
            isKeyboard = isKeyboard,
            onSelected = viewModel::selectTopLevelDestination,
        )
    }
    ExportHost(
        viewModel = viewModel,
        transition = exportTransition,
    )
    // Not while the update required screen covers the app: every one of these is a window of its own on Android,
    // which that screen, drawn inside the activity's content, cannot cover.
    if (!LocalIsCoveredByRequiredUpdate.current) {
        CampfireDialogs(
            viewModel = viewModel,
            urlOpener = urlOpener,
        )
    }
    Messages(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(messagesPadding),
        messageSink = viewModel.messageSink,
        importController = viewModel.importController,
        firstRunController = viewModel.firstRunController,
    )
}
