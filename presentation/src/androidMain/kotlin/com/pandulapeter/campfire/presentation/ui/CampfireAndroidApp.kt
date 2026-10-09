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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotificationPermissionEffect
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import com.pandulapeter.campfire.presentation.ui.theme.appIconThemeColor
import com.pandulapeter.campfire.presentation.ui.platform.rememberAndroidFilePicker
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android shell of the shared UI: keeps the system bar icons in sync with the selected theme (which can differ from
 * the system theme) and lets the URL opener follow it too.
 *
 * @param urlOpener Opens the given URL, styled for the given theme, and says whether anything could open it.
 * @param filesToImport Files from an "open with" or a share, read by the activity that received the intent; the files
 *   of a pick that outlived its process join them here.
 * @param syncNotifier Starts and stops the foreground service a running sync needs, which lives in the application
 *   module because that is where the manifest is.
 * @param metronomeNotifier Starts the media playback service a playing metronome is kept alive and controlled by, for
 *   the same reason.
 * @param onAppReady Released when the app itself is on screen, which is what the activity holds the system splash
 *   screen until: the frame that would otherwise take it away is the launch screen rather than the app.
 * @param onAppIconChanged Told the theme color the launcher icon is to be in (`appIconThemeColor`), once the
 *   preferences have been read and whenever it changes, for the activity to switch the launcher entry to.
 */
@Composable
fun CampfireAndroidApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (url: String, isDarkTheme: Boolean) -> Boolean,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    syncNotifier: SyncNotifier = SyncNotifier { },
    metronomeNotifier: MetronomeNotifier = MetronomeNotifier { },
    onAppReady: () -> Unit = {},
    onAppIconChanged: (UserPreferences.ThemeColor) -> Unit = {},
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isDarkTheme = userPreferences?.uiMode.isDarkTheme()
    val appIconThemeColor = userPreferences?.appIconThemeColor
    LaunchedEffect(appIconThemeColor) { appIconThemeColor?.let(onAppIconChanged) }
    // The permission the foreground service's notification needs, asked for here because the shell is what knows
    // that this platform has one to ask for at all. Only whether sync is connected is collected, since the state
    // changes with every file a run moves and the shell's root would otherwise recompose with it; it starts from the
    // state of the moment, so that a composition starting in the middle of a run does not ask a frame late.
    val isSyncConnected by remember(viewModel) {
        viewModel.syncState.map { it is SyncState.Connected }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(viewModel.syncState.value is SyncState.Connected)
    SyncNotificationPermissionEffect(isSyncConnected = isSyncConnected)
    val activity = LocalActivity.current as? ComponentActivity
    // Android clears the view model with every activity that is destroyed outside a configuration change, finishing
    // or not ("Don't keep activities" destroys it on every trip to the background), and only a finishing one is the
    // app being left. Registered after ComponentActivity's own observer, so it hears ON_DESTROY first: the lifecycle
    // tells observers of a downward move newest first, and the view model is cleared by that older observer.
    DisposableEffect(activity, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            // Not for a configuration change, which keeps the view model: a flag set then would outlive it and answer a
            // later clear that this observer did not hear (an activity destroyed before its first composition).
            if (event == Lifecycle.Event.ON_DESTROY && activity != null && !activity.isChangingConfigurations) {
                viewModel.onHostDestroyed(isFinishing = activity.isFinishing)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(activity, isDarkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
    }
    val filePicker = rememberAndroidFilePicker()
    LaunchedEffect(filePicker, viewModel) {
        filePicker.orphanedExportResults.collect { isWritten -> if (!isWritten) viewModel.onExportFailed() }
    }
    val allFilesToImport = remember(filesToImport, filePicker) { merge(filesToImport, filePicker.orphanedFiles) }
    CompositionLocalProvider(
        LocalFilePicker provides filePicker,
        LocalSyncNotifier provides syncNotifier,
        LocalMetronomeNotifier provides metronomeNotifier,
    ) {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = { url -> if (!urlOpener(url, isDarkTheme)) viewModel.onLinkNotOpened(url) },
            filesToImport = allFilesToImport,
            onAppReady = onAppReady,
        )
    }
}
