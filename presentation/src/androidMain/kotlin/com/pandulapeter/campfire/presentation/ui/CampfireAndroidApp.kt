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

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotificationPermissionEffect
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.rememberAndroidFilePicker
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android shell of the shared UI: keeps the system bar icons in sync with the selected theme (which can differ from
 * the system theme) and lets the URL opener follow it too.
 *
 * @param urlOpener Opens the given URL, styled for the given theme.
 * @param filesToImport Files from an "open with" or a share, read by the activity that received the intent.
 * @param syncNotifier Starts and stops the foreground service a running sync needs, which lives in the application
 *   module because that is where the manifest is.
 * @param onAppReady Released when the app itself is on screen, which is what the activity holds the system splash
 *   screen until: the frame that would otherwise take it away is the launch screen rather than the app.
 */
@Composable
fun CampfireAndroidApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (url: String, isDarkTheme: Boolean) -> Unit,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    syncNotifier: SyncNotifier = SyncNotifier { },
    onAppReady: () -> Unit = {},
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isDarkTheme = userPreferences?.uiMode.isDarkTheme()
    // The permission the foreground service's notification needs, asked for here because the shell is what knows
    // that this platform has one to ask for at all.
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    SyncNotificationPermissionEffect(isSyncConnected = syncState is SyncState.Connected)
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, isDarkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
    }
    CompositionLocalProvider(
        LocalFilePicker provides rememberAndroidFilePicker(),
        LocalSyncNotifier provides syncNotifier,
    ) {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = { urlOpener(it, isDarkTheme) },
            filesToImport = filesToImport,
            onAppReady = onAppReady,
        )
    }
}
