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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.metronome_notification_channel
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeContext
import com.pandulapeter.campfire.presentation.ui.platform.LocalMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotification
import com.pandulapeter.campfire.presentation.ui.platform.areBeatHapticsFeltInBackground
import com.pandulapeter.campfire.presentation.ui.platform.beatHapticsDispatcher
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import com.pandulapeter.campfire.presentation.ui.components.KEY_SEPARATOR
import androidx.lifecycle.repeatOnLifecycle
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_sync_cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_channel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.ui.components.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.components.LocalTagColors
import com.pandulapeter.campfire.presentation.ui.components.ProvideCoverArtImageLoader
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.platform.isLibraryEditableOutsideApp
import com.pandulapeter.campfire.presentation.localization.ApplyLanguagePreference
import com.pandulapeter.campfire.presentation.ui.theme.CampfireTheme
import com.pandulapeter.campfire.presentation.ui.theme.ProvideInterfaceScale
import com.pandulapeter.campfire.presentation.ui.update.AppUpdateGate
import com.pandulapeter.campfire.presentation.ui.launch.LaunchScreenGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/**
 * The root of the shared Compose UI: theme, adaptive navigation chrome, the Navigation 3 display and the dialogs.
 *
 * @param urlOpener Opens the given URL in the platform's browser.
 * @param filesToImport Files the host handed over - opened with Campfire, shared to it, or dropped onto its window.
 * @param onAppReady Called once the app itself is on screen - the same moment `LaunchScreen` is taken away - for the
 *   shells that open on a startup screen of their own and are able to hold it there: Android's system splash and the
 *   loading screen of the web build. Every one of those is otherwise taken away by the first frame the app draws,
 *   and that frame is `LaunchScreen` rather than the app - so without this they would hand over to it and the user
 *   would watch two startup screens in a row.
 * @param onBackgroundColorChanged Called with the theme's background color whenever it changes, every frame of a
 *   cross fade included, for the shells whose window shows a color of its own where the app has not drawn yet: the
 *   desktop's, when it is resized faster than the app is laid out again.
 */
@Composable
fun CampfireApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    onAppReady: () -> Unit = {},
    onBackgroundColorChanged: (Color) -> Unit = {},
) {
    LaunchedEffect(filesToImport) { filesToImport.collect(viewModel::importFiles) }
    val showSyncNotification = rememberSyncNotifications(viewModel)
    MetronomeNotificationEffect(viewModel)
    MetronomeHapticsEffect(viewModel)
    ProvideCoverArtImageLoader()
    // A library the user can reach from outside the app (the desktop folder, the iOS Files app) can also change
    // while the app is away, so it is read again whenever Campfire comes back to the front: ON_START, which is iOS
    // entering the foreground and the desktop window being restored, and not ON_RESUME, which iOS also sends after
    // Control Center, a notification or a system alert. One only the app can see - Android's private storage, the
    // browser's origin private file system - cannot change behind its back, and re-reading every song on each window
    // focus would be cost with nothing to show for it.
    //
    // The desktop window also answers to its focus, since a song edited in another window next to it is brought back
    // with a click rather than a restore - but only once the last rescan is old enough, so that switching between
    // two windows does not re-read the library every time.
    //
    // The first start and the first resume are the ones that follow the initial load, and are skipped.
    if (isLibraryEditableOutsideApp) {
        var hasStartedBefore by remember { mutableStateOf(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            if (hasStartedBefore) viewModel.refresh() else hasStartedBefore = true
        }
        if (isDesktopPlatform) {
            var hasResumedBefore by remember { mutableStateOf(false) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (hasResumedBefore) viewModel.refreshIfStale() else hasResumedBefore = true
            }
        }
    }
    // The editor's unsaved text goes to disk whenever the app stops being the one in front, and a waiting sync run
    // starts, see CampfireViewModel.onAppPaused. ON_PAUSE rather than ON_STOP: it always comes first, and an app swiped
    // away from iOS's app switcher may never have got any further. The run is handed to the platform right here, while
    // the app is still in front: waiting for it to reach the composition would mean waiting for a frame, and a phone
    // whose screen was just turned off pauses and stops the app with none in between.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAppPaused()?.let(showSyncNotification) }
    // A click that cannot sound is stopped once the app has been out of sight for a moment, see
    // CampfireViewModel.onAppStopped. ON_STOP rather than ON_PAUSE: Control Center or a notification shade pulled over
    // the app pauses it with the click still in view.
    val areBeatsFeltInBackground = areBeatHapticsFeltInBackground && rememberBeatHaptics() != null
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppStopped(areBeatsFeltInBackground = areBeatsFeltInBackground) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppStarted() }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val arePreferencesLoaded by viewModel.arePreferencesLoaded.collectAsStateWithLifecycle()
    ApplyLanguagePreference(userPreferences?.language)
    // Menus are counted into the view model's own overlay state rather than a global, so the key handlers and the web
    // build's history that read it read the one this app is composed with.
    CompositionLocalProvider(
        LocalOverlayState provides viewModel.overlayState,
        LocalTagColors provides (userPreferences?.tagColors ?: emptyMap()),
    ) {
        CampfireTheme(
            uiMode = userPreferences?.uiMode,
            themeColor = userPreferences?.themeColor,
            backgroundWarmth = userPreferences?.backgroundWarmth ?: 0,
        ) { isThemeSettled, launchScreenColors ->
            val backgroundColor = MaterialTheme.colorScheme.background
            SideEffect { onBackgroundColorChanged(backgroundColor) }
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Nothing is composed until the preferences have been read: they decide the palette and the language,
                // and the app would otherwise open on the system's guess at both and correct itself a frame later - in
                // an accent color the user did not choose, with labels in a language they did not choose either.
                // Waiting costs the one frame it takes to read a small file, and even that frame is not empty: the
                // window is already painted in the theme's background, which is the one color the palettes agree on
                // within a light or a dark scheme.
                if (arePreferencesLoaded) {
                    // The launch screen is left out of the interface scale: it hands over from the startup screens of the
                    // platforms, which draw the mark at its unscaled size.
                    ProvideInterfaceScale {
                        // Inside the theme, so that the one screen it can put in the way of the app is drawn in the colors
                        // the user chose, and above the language preference, so that it is in the language they chose too.
                        AppUpdateGate(viewModel = viewModel) {
                            CampfireContent(
                                viewModel = viewModel,
                                urlOpener = urlOpener,
                            )
                        }
                    }
                }
                LaunchScreenGate(
                    viewModel = viewModel,
                    arePreferencesLoaded = arePreferencesLoaded,
                    isThemeSettled = isThemeSettled,
                    launchScreenColors = launchScreenColors,
                    onAppReady = onAppReady,
                )
            }
        }
    }
}

/**
 * Tells the platform shell what a running sync should look like while the app is not in front of the user, and that
 * there is nothing to show the moment it ends. Returns the way to hand a run over at once, for the moment the app
 * leaves the front, when there may be no frame left for the state to arrive in.
 *
 * Here rather than on the settings screen because a run outlives the screen that started it: the user is free to go
 * back to their songs, or leave the app entirely, and the notification has to follow the run rather than the screen.
 * The strings are resolved here too, so that the notification is in the language chosen inside the app rather than
 * the system's.
 */
@Composable
private fun rememberSyncNotifications(viewModel: CampfireViewModel): (SyncProgress) -> Unit {
    val syncNotifier = LocalSyncNotifier.current
    // Whether a run is going rather than how far it has got: the shells keep the count moving on their own (see
    // SyncNotification), so a notification handed over for every file a run moves would be work on the main thread
    // for nothing. The count of the moment is read as the notification is handed over, which is what the Android
    // service starts with.
    val isRunning by remember(viewModel) {
        viewModel.syncState.map { it.progress != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(viewModel.syncState.value.progress != null)
    val channelName = stringResource(Res.string.settings_sync_notification_channel)
    val title = stringResource(Res.string.settings_sync_notification_title)
    val stopLabel = stringResource(Res.string.settings_sync_cancel)
    val preparing = stringResource(Res.string.settings_sync_preparing)
    val progressBodyFormat = stringResource(Res.string.settings_sync_progress)
    // "Nothing to show" is only ever said after something was shown from here. Said on the first frame of every
    // composition, it would reach the Android shell as "stop the service" whenever the app was opened onto a run
    // that was already going in the background, and the service takes that as the user's request to stop the run.
    var hasShownNotification by remember { mutableStateOf(false) }
    val show = remember(syncNotifier, channelName, title, stopLabel, preparing, progressBodyFormat) {
        { progress: SyncProgress ->
            hasShownNotification = true
            syncNotifier.onSyncNotificationChanged(
                SyncNotification(
                    channelName = channelName,
                    title = title,
                    preparingBody = preparing,
                    progressBodyFormat = progressBodyFormat,
                    stopLabel = stopLabel,
                    progress = progress,
                ),
            )
        }
    }
    LaunchedEffect(isRunning, show) {
        val progress = viewModel.syncState.value.progress
        if (isRunning && progress != null) {
            show(progress)
        } else if (hasShownNotification) {
            hasShownNotification = false
            syncNotifier.onSyncNotificationChanged(null)
        }
    }
    return show
}

private val SyncState.progress get() = (this as? SyncState.Connected)?.progress

/**
 * Hands a playing click to the shell in the language chosen in the app, see [MetronomeNotifier], and says it is over only
 * once it has said it began: reported on the first frame of every composition, "nothing to show" would reach the Android
 * shell of an activity recreated over a click that is still playing.
 */
@Composable
private fun MetronomeNotificationEffect(viewModel: CampfireViewModel) {
    val notifier = LocalMetronomeNotifier.current
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val pattern = (playback as? MetronomePlayback.Playing)?.pattern
    val songTitle = (viewModel.metronomeContext as? MetronomeContext.Song)?.let { songsByFileName[it.songFileName]?.title }
    val notification = pattern?.let {
        MetronomeNotification(
            channelName = stringResource(Res.string.metronome_notification_channel),
            title = songTitle ?: stringResource(Res.string.metronome),
            body = "${stringResource(Res.string.song_details_tempo, it.bpm.toString())} $KEY_SEPARATOR ${it.timeSignature}",
            stopLabel = stringResource(Res.string.metronome_stop),
        )
    }
    var hasShown by remember { mutableStateOf(false) }
    LaunchedEffect(notification) {
        if (notification != null) {
            hasShown = true
            notifier.onMetronomeNotificationChanged(notification)
        } else if (hasShown) {
            hasShown = false
            notifier.onMetronomeNotificationChanged(null)
        }
    }
}

/**
 * The beat in the hand, from the heard beats, only where the user asked for it: while the app is resumed, or for as long
 * as it is composed where [areBeatHapticsFeltInBackground].
 */
@Composable
private fun MetronomeHapticsEffect(viewModel: CampfireViewModel) {
    val haptics = rememberBeatHaptics() ?: return
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    if (!settings.isHapticBeatEnabled) return
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(haptics, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(if (areBeatHapticsFeltInBackground) Lifecycle.State.CREATED else Lifecycle.State.RESUMED) {
            withContext(beatHapticsDispatcher) {
                viewModel.metronomeBeats.collect { beat ->
                    if (!beat.isSubdivision && beat.level != BeatLevel.MUTED) haptics.onBeat(isAccent = beat.level == BeatLevel.ACCENT)
                }
            }
        }
    }
}
