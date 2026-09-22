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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * iOS shell of the shared UI.
 *
 * @param urlOpener Opens the given URL in Safari.
 * @param filePicker The document picker. Handed in for the same reason as [urlOpener]: both are UIKit, which this
 *   module stays free of.
 * @param filesToImport Files opened with Campfire or shared to it, which reach the app module through `onOpenURL`.
 * @param syncNotifier Holds a background task and shows the notification while a sync run lasts. UIKit again, so it
 *   comes from the app module too.
 * @param onUiModeChanged Called with the theme preference whenever it changes, so that the app module can hand it to
 *   UIKit: the status bar, the system sheets and the keyboard follow the window's interface style, not Compose's. It is
 *   the preference rather than the resolved dark flag, since the system's own mode is read from the very trait
 *   collection the app module overrides, and "System default" has to leave it free to follow the phone.
 */
@Composable
fun CampfireIosApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit,
    filePicker: FilePicker,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    syncNotifier: SyncNotifier = SyncNotifier { },
    onUiModeChanged: (UserPreferences.UiMode?) -> Unit = {},
) = CompositionLocalProvider(
    LocalFilePicker provides filePicker,
    LocalSyncNotifier provides syncNotifier,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val uiMode = userPreferences?.uiMode
    LaunchedEffect(uiMode) { onUiModeChanged(uiMode) }
    CampfireApp(
        viewModel = viewModel,
        urlOpener = urlOpener,
        filesToImport = filesToImport,
    )
}
