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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.IosClipboardLinkReader
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import com.pandulapeter.campfire.presentation.ui.theme.appIconColor
import com.pandulapeter.campfire.presentation.ui.theme.appIconThemeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * iOS shell of the shared UI, which also takes what is dragged onto it from another app ([libraryDropTarget]).
 *
 * @param urlOpener Opens the given URL in Safari.
 * @param filePicker The document picker. Handed in for the same reason as [urlOpener]: both are UIKit, which this
 *   module stays free of.
 * @param filesToImport Files opened with Campfire, which reach the app module through `onOpenURL`.
 * @param shareLinks Campfire links the app was opened with (the Universal Link, `campfire://s/`), which reach the app
 *   module through `onOpenURL` like files.
 * @param inboxShares Shares the share extension left in its inbox, one at a time: each is completed as taken once its
 *   import has settled. Completed from the import rather than awaited here, since the queue lives in the view model
 *   and carries on when this composition goes.
 * @param syncNotifier Holds a background task and shows the notification while a sync run lasts. UIKit again, so it
 *   comes from the app module too.
 * @param metronomeNotifier Shows a playing click in Now Playing and answers the lock screen's controls, for the same
 *   reason.
 * @param onUiModeChanged Called with the theme preference whenever it changes, so that the app module can hand it to
 *   UIKit: the status bar, the system sheets and the keyboard follow the window's interface style, not Compose's. It is
 *   the preference rather than the resolved dark flag, since the system's own mode is read from the very trait
 *   collection the app module overrides, and "System default" has to leave it free to follow the phone.
 * @param onAppIconChanged Called with the color the home screen icon should be in, for the app module to switch to the
 *   alternate icon of that color. Not as soon as the preference changes, but once it has stayed the same for a moment
 *   while the app is in front: iOS answers every switch with an alert, which a user trying the swatches one after the
 *   other would otherwise get one of for each, and it refuses one asked for by an app that is not active. It is asked
 *   again every time the app comes back to the front, which is what puts right a switch that was refused, or one that
 *   a restored backup brought a preference for, and is nothing at all where the icon already matches.
 */
@Composable
fun CampfireIosApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit,
    filePicker: FilePicker,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    shareLinks: Flow<SharedLink> = emptyFlow(),
    inboxShares: Flow<InboxShare> = emptyFlow(),
    syncNotifier: SyncNotifier = SyncNotifier { },
    metronomeNotifier: MetronomeNotifier = MetronomeNotifier { },
    onUiModeChanged: (UserPreferences.UiMode?) -> Unit = {},
    onAppIconChanged: (UserPreferences.ThemeColor) -> Unit = {},
) = CompositionLocalProvider(
    LocalFilePicker provides filePicker,
    LocalSyncNotifier provides syncNotifier,
    LocalMetronomeNotifier provides metronomeNotifier,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val uiMode = userPreferences?.uiMode
    LaunchedEffect(uiMode) { onUiModeChanged(uiMode) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val appIconColor = userPreferences.appIconThemeColor.appIconColor
    // An unread preference says nothing about the icon, which is the one the user last chose until it is read.
    if (userPreferences != null) {
        LaunchedEffect(appIconColor, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(APP_ICON_DELAY_MILLISECONDS)
                onAppIconChanged(appIconColor)
            }
        }
    }
    // Asked as the app starts, without reading the pasteboard: the view model asks only once, and only on a first launch.
    LaunchedEffect(viewModel) { viewModel.firstRunController.offerClipboardLink(IosClipboardLinkReader) }
    LaunchedEffect(inboxShares) {
        inboxShares.collect { share ->
            val settled = share.texts?.let(viewModel.pastedSongController::importSharedText)
                ?: viewModel.importController.importFiles(share.files)
            settled.invokeOnCompletion { share.taken.complete(Unit) }
        }
    }
    Box(modifier = Modifier.fillMaxSize().libraryDropTarget(viewModel)) {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = urlOpener,
            filesToImport = filesToImport,
            shareLinks = shareLinks,
        )
    }
}

private const val APP_ICON_DELAY_MILLISECONDS = 1000L
