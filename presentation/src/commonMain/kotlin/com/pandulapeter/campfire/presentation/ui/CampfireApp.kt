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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.error_link_not_opened
import com.pandulapeter.campfire.presentation.resources.error_operation_failed
import com.pandulapeter.campfire.presentation.resources.export_failed
import com.pandulapeter.campfire.presentation.resources.export_library_saved
import com.pandulapeter.campfire.presentation.resources.export_pdf_saved
import com.pandulapeter.campfire.presentation.resources.export_setlist_saved
import com.pandulapeter.campfire.presentation.resources.export_skipped_files
import com.pandulapeter.campfire.presentation.resources.export_song_saved
import com.pandulapeter.campfire.presentation.resources.export_too_large_to_import
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.metronome_notification_channel
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_disconnected
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_failed
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_interrupted
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_refused
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_silent
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeContext
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.platform.LocalMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotification
import com.pandulapeter.campfire.presentation.ui.platform.areBeatHapticsFeltInBackground
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import com.pandulapeter.campfire.presentation.ui.screens.metronome.MetronomeScreen
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.KEY_SEPARATOR
import androidx.lifecycle.repeatOnLifecycle
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_converted
import com.pandulapeter.campfire.presentation.resources.import_details
import com.pandulapeter.campfire.presentation.resources.import_open
import com.pandulapeter.campfire.presentation.resources.import_status_stopped
import com.pandulapeter.campfire.presentation.resources.import_result
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_sync_cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_channel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_lost
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_restored
import com.pandulapeter.campfire.presentation.resources.song_editor_file_gone
import com.pandulapeter.campfire.presentation.resources.song_editor_save_failed
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_partly
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name_partly
import com.pandulapeter.campfire.presentation.ui.components.ListLayout
import com.pandulapeter.campfire.presentation.ui.components.NavigationItemPresence
import com.pandulapeter.campfire.presentation.ui.components.ProvideCoverArtImageLoader
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.components.collapsingNavigationItem
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.pluralTextResource
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.dialogs.CampfireDialogs
import com.pandulapeter.campfire.presentation.ui.dialogs.ExportHost
import com.pandulapeter.campfire.presentation.ui.dialogs.ExportTransition
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.areDrawablesLoaded
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.platform.isLaunchScreenWholeStartup
import com.pandulapeter.campfire.presentation.ui.platform.isLibraryEditableOutsideApp
import com.pandulapeter.campfire.presentation.ui.platform.isStartupScreenHeldUntilAppReady
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistsScreen
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReportScreen
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsScreen
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDetailsScreen
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.SongEditorScreen
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongsScreen
import com.pandulapeter.campfire.presentation.ui.theme.ApplyLanguagePreference
import com.pandulapeter.campfire.presentation.ui.theme.CampfireTheme
import com.pandulapeter.campfire.presentation.ui.theme.LaunchScreenColors
import com.pandulapeter.campfire.presentation.ui.theme.ProvideInterfaceScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * The root of the shared Compose UI: theme, adaptive navigation chrome, the Navigation 3 display and the dialogs.
 *
 * @param urlOpener Opens the given URL in the platform's browser.
 * @param filesToImport Files the host handed over - opened with Campfire, shared to it, or dropped onto its window.
 * @param onAppReady Called once the app itself is on screen - the same moment [LaunchScreen] is taken away - for the
 *   shells that open on a startup screen of their own and are able to hold it there: Android's system splash and the
 *   loading screen of the web build. Every one of those is otherwise taken away by the first frame the app draws,
 *   and that frame is [LaunchScreen] rather than the app - so without this they would hand over to it and the user
 *   would watch two startup screens in a row.
 * @param onBackgroundColorChanged Called with the theme's background color whenever it changes, every frame of a
 *   cross fade included, for the shells whose window shows a color of its own where the app has not drawn yet: the
 *   desktop's, when it is resized faster than the app is laid out again.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val hasLibraryToShow by viewModel.hasLibraryToShow.collectAsStateWithLifecycle()
    ApplyLanguagePreference(userPreferences?.language)
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
            // The launch screen covers the app rather than standing in for it, and it fades away once there is
            // something to look at underneath: the app composes, lays out and draws behind it in the meantime, so
            // holding it through the first read of the library costs none of the time that read was going to take
            // anyway. What it covers is the handful of frames the song list would otherwise open on its loading
            // indicator for, and a startup screen handing over to a spinner is two startup screens in a row - the
            // very thing onAppReady exists to keep the other shells from doing.
            //
            // The colors have to have stopped moving as well, or the launch screen would be taken away halfway
            // through its own cross fade from the theme the window opened on to the stored one (the app underneath
            // snaps to it at once, see CampfireTheme).
            //
            // Read once: the composition that took the launch screen away is not always the one drawing the app
            // (Android recreates its activity on the configuration changes it does not handle itself, see the
            // manifest, and when the system reclaims it), and one that starts after it has nothing to cover.
            val hasShownAppBefore = remember { viewModel.hasShownApp }
            var isAppReady by remember { mutableStateOf(hasShownAppBefore) }
            if (hasShownAppBefore) {
                // The shell still holds a startup screen of its own until it is told, the Android activity's pre-draw
                // gate.
                LaunchedEffect(Unit) { onAppReady() }
            } else if (isAppReady && isStartupScreenHeldUntilAppReady) {
                // Outside the launch screen's own block, which leaves the composition - and cancels its effects - the
                // moment the app is uncovered. The two frames are there for the reason given below: the composition
                // without the launch screen has to be the one being drawn when the shell lets go, or Android's first
                // frame would be the mark after all.
                LaunchedEffect(Unit) {
                    repeat(2) { withFrameNanos { } }
                    onAppReady()
                }
            }
            if (!isAppReady) {
                val opacity = remember { Animatable(1f) }
                // In the desktop application the mark grows as it goes, over the slower of the two effect springs so
                // that the movement has the time to be read as one: this is the one platform where the launch screen is
                // the whole of the startup - the first frame the window paints and the last one before the app - so it
                // is worth leaving by opening into the app rather than by merely thinning out. The other three
                // open on a startup screen of their own and never watch this one go: Android's splash and the web's
                // loading page are still over it when it goes, so there it does not fade at all, and on iOS the mark
                // is already the second thing shown - so there it stays the plain, quicker dissolve, and the extra
                // frames are not spent.
                val markGrowth = if (isLaunchScreenWholeStartup) LAUNCH_MARK_EXIT_GROWTH else 0f
                LaunchScreen(
                    modifier = Modifier.graphicsLayer { alpha = opacity.value.coerceIn(0f, 1f) },
                    colors = launchScreenColors,
                    markScale = { 1f + (1f - opacity.value.coerceIn(0f, 1f)) * markGrowth },
                )
                val motionScheme = MaterialTheme.motionScheme
                val fadeSpec = if (isLaunchScreenWholeStartup) motionScheme.slowEffectsSpec<Float>() else motionScheme.defaultEffectsSpec<Float>()
                // The icons have to be in as well, or the app would be uncovered while it is still fetching them one
                // by one and every list row, button and chip holding one would resize around it as it lands. Asking
                // for them here rather than anywhere earlier is what pays for the wait out of time the launch screen
                // was up for anyway, and on the three platforms that read a drawable without suspending this is
                // constantly true and costs nothing.
                val areDrawablesLoaded = areDrawablesLoaded()
                LaunchedEffect(arePreferencesLoaded, hasLibraryToShow, isThemeSettled, areDrawablesLoaded) {
                    if (arePreferencesLoaded && hasLibraryToShow && isThemeSettled && areDrawablesLoaded) {
                        if (isStartupScreenHeldUntilAppReady) {
                            // Nothing under the shell's own startup screen is seen, so the fade would only hold the
                            // app back: the app is uncovered at once, and the effect below lets the shell go.
                            isAppReady = true
                            viewModel.hasShownApp = true
                        } else {
                            // Two frames rather than one, because withFrameNanos resumes while the frame it belongs
                            // to is still being assembled: the frame after it is the first one that is certainly drawn.
                            repeat(2) { withFrameNanos { } }
                            opacity.animateTo(0f, fadeSpec)
                            isAppReady = true
                            viewModel.hasShownApp = true
                            // Only now, so that the shells holding a startup screen of their own hand over to the app
                            // itself rather than to the last frames of a mark fading off it.
                            onAppReady()
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the window holds until there is an app to look at in it: the mark, still, on the theme's own background.
 *
 * It is what the first frame of every platform paints, and on the desktop it is what stays there for as long as the
 * first composition of the whole app takes - a third of a second on a cold start, which is a long time for a window
 * to sit empty. Nothing here says anything the preferences have not answered yet: the mark carries no text, and it
 * is drawn in a neutral rather than in the accent color, which is the one thing still being waited for.
 *
 * A [Surface] rather than a plain box, because the app is composed and drawn underneath it: without one, a click
 * landing on the mark would reach whatever of the app happens to be under that point - which stays true while it is
 * fading, when the app can already be seen through it but is still nobody's to touch. The surface itself is
 * transparent, and the background and the mark are painted while drawing from [colors], so that their fade from the
 * guessed palette to the stored one recomposes nothing.
 *
 * @param markScale How large the mark is drawn, read in the layer rather than in the composition so that animating
 *   it is a redraw instead of a recomposition of the whole screen.
 */
@Composable
private fun LaunchScreen(
    modifier: Modifier = Modifier,
    colors: LaunchScreenColors,
    markScale: () -> Float = { 1f },
) = Surface(
    modifier = modifier
        .fillMaxSize()
        .drawBehind { drawRect(colors.background()) },
    color = Color.Transparent,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val painter = painterResource(Res.drawable.ic_campfire)
        Box(
            modifier = Modifier
                .size(LAUNCH_MARK_SIZE)
                .graphicsLayer {
                    scaleX = markScale()
                    scaleY = markScale()
                }
                .drawBehind { with(painter) { draw(size = size, colorFilter = ColorFilter.tint(colors.mark())) } },
        )
    }
}

/**
 * The window insets a screen hands to its scrolling content: what the system bars and the chrome leave of the
 * edges, and at the bottom the keyboard wherever it reaches higher than that.
 *
 * The keyboard is asked about when the padding is used rather than when it is made. Its inset is animated, so a
 * composition that reads it runs again on every frame the keyboard is moving for, and the composition these are
 * made in holds the whole app - the navigation display, every screen on it and the song list with them. What
 * uses a padding is a layout, and a layout that reads a state that has changed is only laid out again.
 *
 * @param coveredHeight How much of the keyboard's height is taken by chrome it slides over rather than pushes
 *   away, which is the navigation bar's.
 * @param ime Held as a state because the platforms other than Android hand out a new instance on every
 *   composition, and two of these have to be equal for as long as nothing but the keyboard has moved, or every
 *   screen would be recomposed whenever this composition is.
 */
@Stable
private class KeyboardAwarePadding(
    private val start: Dp,
    private val end: Dp,
    private val bottom: Dp,
    private val coveredHeight: Dp,
    private val ime: State<WindowInsets>,
    private val density: Density,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) start else end

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) end else start

    override fun calculateBottomPadding() = maxOf(bottom, with(density) { ime.value.getBottom(this).toDp() } - coveredHeight)

    override fun equals(other: Any?) = other is KeyboardAwarePadding &&
            start == other.start &&
            end == other.end &&
            bottom == other.bottom &&
            coveredHeight == other.coveredHeight &&
            ime === other.ime &&
            density == other.density

    override fun hashCode() = listOf(start, end, bottom, coveredHeight, density).hashCode()
}

/**
 * The edges the screens keep their content clear of: the system bars and, where the window is laid out into it, the
 * display cutout. Android's edge to edge window reaches into the cutout on every side, and Material's app bars, rail
 * and navigation bar already keep clear of it there (their default insets are these); a camera in the middle of a
 * landscape phone's long edge would otherwise be drawn over the ends of the list rows and the lyrics. Elsewhere the
 * cutout is either nothing or inside the system bars already, which a union leaves as it is.
 */
internal val WindowInsets.Companion.contentEdges: WindowInsets
    @Composable get() = systemBars.union(displayCutout)

/**
 * The least room the navigation rail and the top level screens keep above what they hold. A status bar or the desktop
 * title bar's strip already leaves more than this, but a window with neither - the web, Linux, a desktop window in full
 * screen - would have the rail's first indicator and the list app bar's pill start 4dp from its top edge, closer than
 * either is to the window's sides. The rail and the screens take the same amount, so the two stay level.
 */
private val MIN_TOP_EDGE = 8.dp

private val WindowInsets.withMinTopEdge: WindowInsets
    get() = union(WindowInsets(top = MIN_TOP_EDGE))

@Composable
private fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val backStack = viewModel.backStack
    val topLevelDestinations by viewModel.topLevelDestinations.collectAsStateWithLifecycle()
    var isNavigationTransitionRunning by remember { mutableStateOf(false) }
    var isChromeInScreens by remember { mutableStateOf(false) }
    val isTopLevelScreenCovered = backStack.lastOrNull() !is CampfireDestination.TopLevel
    // The chrome moves into the top level screens as soon as a card covers them, which is the frame the push starts
    // in, and moves back out only once the pop has settled. NavDisplay starts animating a pop from an effect, so its
    // transition is only reported as running a frame after the back stack changed, and reading the report any sooner
    // would take the pop for one that had already settled: the transition is given two frames to start before it is
    // waited on, and one that never starts (nothing animates behind the launch screen) is simply not waited on.
    LaunchedEffect(isTopLevelScreenCovered) {
        if (isTopLevelScreenCovered) {
            isChromeInScreens = true
        } else {
            repeat(2) { withFrameNanos { } }
            snapshotFlow { isNavigationTransitionRunning }.first { !it }
            isChromeInScreens = false
        }
    }
    val chromeInScreens = isChromeInScreens || isTopLevelScreenCovered
    val metronomeSettings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val metronomeBeat = rememberMetronomeIconBeat(
        playback = viewModel.metronomePlayback,
        beats = viewModel.metronomeBeats,
        isEnabled = metronomeSettings.isVisualBeatEnabled,
    )

    NavigationChromeScaffold(
        // Painted here as well as on every screen, so that the two screens of a cross fading tab transition blend
        // into the same color they are painted in and the fade stays invisible.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        isChromePlaced = !chromeInScreens,
        chrome = { chromeKind ->
            NavigationChrome(
                kind = chromeKind,
                destinations = topLevelDestinations,
                currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel,
                metronomeBeat = metronomeBeat,
                onDestinationSelected = viewModel::selectTopLevelDestination,
            )
        },
    ) { windowWidth, windowSize, chromeKind, chromeSize ->
        CampfireScreens(
            viewModel = viewModel,
            urlOpener = urlOpener,
            windowWidth = windowWidth,
            windowSize = windowSize,
            chromeKind = chromeKind,
            chromeSize = chromeSize,
            chromeInScreens = chromeInScreens,
            metronomeBeat = metronomeBeat,
            onNavigationTransitionRunningChanged = { isNavigationTransitionRunning = it },
        )
    }
}

/**
 * The [NavDisplay] and everything laid out over it, sized for the window [NavigationChromeScaffold] measured.
 *
 * While [chromeInScreens] is set, every top level screen draws a navigation chrome of its own, under itself and
 * selected on its own destination, so that the bar or the rail moves with the screen when a card is dealt over it or
 * taken off it, the predictive back gesture included, instead of standing still while the screen slides past it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CampfireScreens(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
    windowWidth: Dp,
    windowSize: WindowSize,
    chromeKind: NavigationChromeKind,
    chromeSize: NavigationChromeSize,
    chromeInScreens: Boolean,
    metronomeBeat: MetronomeIconBeat,
    onNavigationTransitionRunningChanged: (Boolean) -> Unit,
) {
    val backStack = viewModel.backStack
    val topLevelDestinations by viewModel.topLevelDestinations.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    // Makes interrupted transitions retarget instead of getting stuck, see CampfireViewModel.navigationGeneration.
    val navigationMetadata = mapOf(NAVIGATION_GENERATION_METADATA_KEY to viewModel.navigationGeneration)

    // What the chrome takes out of the window on this frame, which follows it while it changes shape, and what it will
    // have taken out once it has settled. The insets are the first and the column counts the second.
    val railWidth = chromeSize.railWidth
    val navigationBarHeight = chromeSize.barHeight

    // The screens next to the chrome always settle at the width the chrome leaves them, whether or not one of them
    // happens to be covered right now; the song details screen covers the chrome, so it settles at the full width.
    // Anything a screen has to decide once, before it is first drawn, is decided from these rather than from the
    // width it is being measured at: the column counts of the song lists and the lyrics, and whether the lists have
    // room for their filter side panel.
    val settledListWidth = windowWidth - chromeSize.settledRailWidth
    val settledSongDetailsWidth = windowWidth

    // What is left of the system bars and the display cutout once the chrome has covered the edge it sits on. The screens
    // hand these to their lists as content padding, so that items scroll under the system bars instead of stopping short
    // of them.
    val systemBars = WindowInsets.contentEdges.asPaddingValues()
    // Never read here, see KeyboardAwarePadding.
    val ime = rememberUpdatedState(WindowInsets.ime)
    val shellContentPadding: PaddingValues = KeyboardAwarePadding(
        start = if (windowSize.usesNavigationRail) 0.dp else systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        bottom = if (windowSize.usesNavigationRail) systemBars.calculateBottomPadding() else 0.dp,
        // The keyboard covers the navigation bar instead of pushing it away, so only what is left of it counts.
        coveredHeight = navigationBarHeight,
        ime = ime,
        density = density,
    )
    val songEditorContentPadding: PaddingValues = KeyboardAwarePadding(
        start = systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        bottom = systemBars.calculateBottomPadding(),
        coveredHeight = 0.dp,
        ime = ime,
        density = density,
    )
    // Nothing on the song details screen is typed into, so its padding does not follow the keyboard: the one a dialog
    // opened from its header brings up would otherwise reflow the lyrics behind that dialog, into more columns and
    // back, and lift the setlist's pager bar, on every frame of its slide in and out.
    val songDetailsContentPadding = PaddingValues(
        start = systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        bottom = systemBars.calculateBottomPadding(),
    )
    // The snackbar sits above the chrome, and above the keyboard wherever that reaches higher: the app is laid out
    // under the keyboard rather than resized by it, and a message sent while somebody is typing - a save that failed
    // in the editor - would otherwise time out behind it unseen.
    val messagesPadding: PaddingValues = KeyboardAwarePadding(
        start = systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        // The navigation chrome's measured height already includes its bottom system inset.
        bottom = maxOf(navigationBarHeight, systemBars.calculateBottomPadding()),
        coveredHeight = 0.dp,
        ime = ime,
        density = density,
    )
    // The width changes on every frame of a window being resized, so the top level screens are handed only what they
    // decide from it, which stays equal between two breakpoints and lets them skip those frames. The song details
    // screen keeps the width itself: its lyrics use the difference between the settled width and the one they are
    // measured at inside their layout, which no discrete decision stands in for.
    val listLayout = ListLayout.of(settledWidth = settledListWidth, contentPadding = shellContentPadding, layoutDirection = layoutDirection)
    val settingsLayout = SettingsWidthLayout.of(settledWidth = settledListWidth, contentPadding = shellContentPadding, layoutDirection = layoutDirection)
    val screenChrome: (CampfireDestination.TopLevel) -> (@Composable () -> Unit)? = { destination ->
        if (chromeInScreens) {
            {
                NavigationChrome(
                    kind = chromeKind,
                    destinations = topLevelDestinations,
                    currentTopLevelDestination = destination,
                    metronomeBeat = metronomeBeat,
                    onDestinationSelected = viewModel::selectTopLevelDestination,
                )
            }
        } else {
            null
        }
    }
    // What the screens' own lifecycles are compared with, see ScreenSurface.
    val hostLifecycle = LocalLifecycleOwner.current.lifecycle
    val exportTransition = remember { ExportTransition() }
    val navigationScrim = remember { NavigationScrim() }
    val scrimColor = MaterialTheme.colorScheme.scrim
    // The export screen is drawn in this layout rather than in a window of its own, which a screen reader would take for
    // modal, so the app under it is taken out of the semantics tree while it covers it - only once fully, so that the
    // screen a back gesture is revealing is not empty to an accessibility service halfway through the swipe. Derived,
    // so that the app is not recomposed on every frame of the slide.
    val isExportCovering by remember { derivedStateOf { exportTransition.progress.value >= 1f } }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NavDisplay(
            // The export screen is dealt over the screens the way a destination is, so they give way to it the same way.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -backgroundSlideOffset((exportTransition.progress.value * size.width).roundToInt()).toFloat() }
                .coveredScreenScrim(scrimColor) { exportTransition.progress.value }
                .then(if (isExportCovering) Modifier.clearAndSetSemantics { } else Modifier),
            backStack = backStack,
            // NavDisplay's own back handler is the system's back, which completes a predictive gesture as often as not.
            onBack = { viewModel.navigateBack(isPredictiveBackCompleted = navigationScrim.isPredictiveBack) },
            // The same spec decides the direction for both parameters, see navigationTransition. Nothing is animated
            // while the launch screen still covers the app: a place the app was asked to open on (the web build's
            // address, Settings after a consent page) is put on the stack behind it, and a screen still sliding in as
            // the launch screen fades would be the app arriving twice.
            transitionSpec = { if (viewModel.hasShownApp) navigationTransition(motionScheme, navigationScrim) else instantTransition(navigationScrim) },
            popTransitionSpec = { if (viewModel.hasShownApp) navigationTransition(motionScheme, navigationScrim) else instantTransition(navigationScrim) },
            predictivePopTransitionSpec = { predictivePopTransition(navigationScrim) },
            // Stable string content keys, so that the transitions can recognize the top level destinations.
            entryProvider = entryProvider {
                entry<CampfireDestination.Songs>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        scrim = navigationScrim,
                        windowSize = windowSize,
                        railWidth = railWidth,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        SongsScreen(
                            viewModel = viewModel,
                            layout = listLayout,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Setlists>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        scrim = navigationScrim,
                        windowSize = windowSize,
                        railWidth = railWidth,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        SetlistsScreen(
                            viewModel = viewModel,
                            layout = listLayout,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Metronome>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        scrim = navigationScrim,
                        windowSize = windowSize,
                        railWidth = railWidth,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        MetronomeScreen(
                            viewModel = viewModel,
                            layout = settingsLayout,
                            scrollPosition = viewModel.metronomeScrollPosition,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Settings>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        scrim = navigationScrim,
                        windowSize = windowSize,
                        railWidth = railWidth,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        SettingsScreen(
                            viewModel = viewModel,
                            layout = settingsLayout,
                            contentPadding = shellContentPadding,
                            isNavigationRailVisible = windowSize.usesNavigationRail,
                            urlOpener = urlOpener,
                        )
                    }
                }
                // These three cover the chrome, so they are the only ones laid out edge to edge.
                entry<CampfireDestination.SongEditor>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    ScreenSurface(hostLifecycle = hostLifecycle, scrim = navigationScrim) {
                        SongEditorScreen(
                            viewModel = viewModel,
                            destination = destination,
                            windowSize = windowSize,
                            contentPadding = songEditorContentPadding,
                            urlOpener = urlOpener,
                            onBack = viewModel::navigateBack,
                        )
                    }
                }
                entry<CampfireDestination.ImportReport>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    ScreenSurface(hostLifecycle = hostLifecycle, scrim = navigationScrim) {
                        ImportReportScreen(
                            viewModel = viewModel,
                            // Its search is typed into, so its list ends above the keyboard as the editor's text does.
                            contentPadding = songEditorContentPadding,
                            onBack = viewModel::navigateBack,
                        )
                    }
                }
                entry<CampfireDestination.SongDetails>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    ScreenSurface(hostLifecycle = hostLifecycle, scrim = navigationScrim) {
                        SongDetailsScreen(
                            viewModel = viewModel,
                            destination = destination,
                            settledWidth = settledSongDetailsWidth,
                            contentPadding = songDetailsContentPadding,
                            onBack = viewModel::navigateBack,
                        )
                    }
                }
            },
        )
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
            viewModel = viewModel,
        )
    }
}

/**
 * The one line of text the app has to say after something it was asked to do has finished. It sits above the
 * navigation chrome rather than inside a screen, because the screen an import was started from is often not the one
 * the user is looking at when it ends.
 */
@Composable
private fun Messages(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Only the head of the view model's queue is read: the text of a message can only be built in a composition
    // (string resources are composable), and two identical results in a row are still two messages - which is what
    // the numbering is for. Two failed exports are the same object, and an effect keyed on the message alone would not
    // restart for the second one: it would sit at the head of the queue forever, unshown, with everything after it
    // stuck behind it. A message that was on screen as the composition was recreated is shown again from the start,
    // since it was cut short.
    val queue by viewModel.messageQueue.collectAsStateWithLifecycle()
    val head = queue.firstOrNull()
    val text = when (val current = head?.value) {
        is CampfireViewModel.Message.ImportFinished -> listOfNotNull(
            if (current.result.isFailed) stringResource(Res.string.import_status_stopped) else null,
            stringResource(
                Res.string.import_result,
                current.result.importedSongFileNames.size,
                current.result.importedSetlistFileNames.size,
                current.result.duplicateFileNames.size,
                current.result.skippedFileNames.size,
            ),
            if (current.result.convertedSongFileNames.isNotEmpty()) stringResource(Res.string.import_converted, current.result.convertedSongFileNames.size) else null,
        ).joinToString("\n")

        CampfireViewModel.Message.ImportFailed -> stringResource(Res.string.import_failed)
        CampfireViewModel.Message.ExportFailed -> stringResource(Res.string.export_failed)
        CampfireViewModel.Message.PdfSaved -> stringResource(Res.string.export_pdf_saved)
        CampfireViewModel.Message.SongExported -> stringResource(Res.string.export_song_saved)
        CampfireViewModel.Message.SetlistExported -> stringResource(Res.string.export_setlist_saved)
        CampfireViewModel.Message.LibraryExported -> stringResource(Res.string.export_library_saved)
        CampfireViewModel.Message.ExportTooLargeToImport -> stringResource(Res.string.export_too_large_to_import)
        is CampfireViewModel.Message.ExportSkippedFiles -> pluralTextResource(
            Res.plurals.export_skipped_files,
            current.fileNames.size,
            current.fileNames.size.toString(),
            current.fileNames.take(MAXIMUM_NAMED_FILES).joinToString(),
        )

        CampfireViewModel.Message.SaveFailed -> stringResource(Res.string.song_editor_save_failed)
        CampfireViewModel.Message.EditorDraftLost -> stringResource(Res.string.song_editor_draft_lost)
        CampfireViewModel.Message.EditorDraftRestored -> stringResource(Res.string.song_editor_draft_restored)
        CampfireViewModel.Message.EditedSongFileGone -> stringResource(Res.string.song_editor_file_gone)
        CampfireViewModel.Message.OperationFailed -> stringResource(Res.string.error_operation_failed)
        CampfireViewModel.Message.SongFileRenamedPartly -> stringResource(Res.string.songs_update_file_name_partly)
        CampfireViewModel.Message.SongDeletedPartly -> stringResource(Res.string.songs_delete_song_partly)
        is CampfireViewModel.Message.LinkNotOpened -> textResource(Res.string.error_link_not_opened, current.url)
        is CampfireViewModel.Message.MetronomeStopped -> stringResource(
            when (current.reason) {
                MetronomeStopReason.AUDIO_REFUSED -> Res.string.metronome_stopped_refused
                MetronomeStopReason.AUDIO_INTERRUPTED -> Res.string.metronome_stopped_interrupted
                MetronomeStopReason.OUTPUT_DISCONNECTED -> Res.string.metronome_stopped_disconnected
                MetronomeStopReason.OUTPUT_FAILED -> Res.string.metronome_stopped_failed
            }
        )
        CampfireViewModel.Message.SilentMetronomeStopped -> stringResource(Res.string.metronome_stopped_silent)
        null -> null
    }
    val importFinished = head?.value as? CampfireViewModel.Message.ImportFinished
    val songToOpen = importFinished?.result?.convertedSongToOpen
    val actionLabel = when {
        songToOpen != null -> stringResource(Res.string.import_open)
        importFinished?.hasDetails == true -> stringResource(Res.string.import_details)
        else -> null
    }
    LaunchedEffect(head?.index) {
        if (head != null && text != null) {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = if (actionLabel != null || '\n' in text) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                when {
                    songToOpen != null -> viewModel.openImportedSong(songToOpen)
                    importFinished != null -> viewModel.openImportReport(importFinished.result)
                }
            }
            viewModel.onMessageShown(head)
        }
    }
    SnackbarHost(
        modifier = modifier,
        hostState = snackbarHostState,
    ) { data ->
        Snackbar(snackbarData = data)
    }
}

/**
 * Lays the navigation chrome out and hands [content] the size of the window along with what the chrome takes out of
 * it, all in one pass.
 *
 * The thickness has to be *measured*: Material keeps the size of the rail and of the bar - and of the insets they
 * cover - to itself. Reporting it back as state from the laid out chrome would report it one layout pass too late,
 * so the app's very first frame would be composed as if the window had no chrome in it at all: the screens would
 * cover the rail and settle their column counts for the full width, only to be laid out again a frame later. That
 * frame is not a fleeting one either - it is the first frame of a cold start, and the second one is several
 * hundred milliseconds behind it while everything the app draws with is still being loaded.
 *
 * Subcomposing the chrome ahead of the content is what makes its size available to the composition that needs it.
 * It costs no layer that was not there already, since the window size the screens are laid out for used to come
 * through a `BoxWithConstraints`, which is a [SubcomposeLayout] of exactly this kind.
 *
 * The chrome is placed *under* [content]: the song details screen is dealt over the whole window and covers it,
 * rather than the two of them animating side by side. While the top level screens draw a chrome of their own
 * ([isChromePlaced] off), this one is still measured, for its thickness, but not placed, so it is neither drawn nor
 * touched; it stays composed, so the state of its items carries on once it is placed again.
 *
 * A window that crosses from one kind of chrome to another (see [NavigationChromeKind]) does not switch between them
 * in one frame: the chrome it is leaving fades out towards its edge while the one it is getting fades in from its own,
 * and the room they take out of the window is worked out between the two on the same spring, so the screens next to
 * them travel rather than jump. The chrome is still measured for the kind the window has *now*, in the frame the
 * window changes, so what the screens settle at is known at once ([NavigationChromeSize.settledRailWidth]) while what
 * they are inset by catches up.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavigationChromeScaffold(
    modifier: Modifier = Modifier,
    isChromePlaced: Boolean,
    chrome: @Composable (kind: NavigationChromeKind) -> Unit,
    content: @Composable (windowWidth: Dp, windowSize: WindowSize, kind: NavigationChromeKind, size: NavigationChromeSize) -> Unit,
) {
    val transition = remember { NavigationChromeTransition() }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(transition.generation) {
        if (transition.generation > 0) {
            animate(initialValue = 0f, targetValue = 1f, animationSpec = spec) { value, _ -> transition.progress = value }
            transition.outgoingKind = null
        }
    }
    SubcomposeLayout(modifier) { constraints ->
        val windowWidth = constraints.maxWidth.toDp()
        val windowSize = WindowSize.fromWidth(windowWidth)
        val kind = navigationChromeKind(windowWidth)
        // Loose constraints, so that the rail and the bar each take only the one dimension they want.
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val chromePlaceable = subcompose(ChromeSlot.CHROME) { chrome(kind) }.single().measure(looseConstraints)
        transition.update(kind = kind, railWidth = if (kind.isRail) chromePlaceable.width else 0, barHeight = if (kind.isRail) 0 else chromePlaceable.height)
        val outgoingKind = transition.outgoingKind
        val outgoingPlaceable = outgoingKind?.let { subcompose(ChromeSlot.OUTGOING_CHROME) { chrome(it) }.single().measure(looseConstraints) }
        val progress = transition.progress
        val chromeSize = NavigationChromeSize(
            railWidth = lerp(transition.fromRailWidth, transition.toRailWidth, progress).toDp(),
            barHeight = lerp(transition.fromBarHeight, transition.toBarHeight, progress).toDp(),
            settledRailWidth = transition.toRailWidth.toDp(),
        )
        val contentPlaceable = subcompose(ChromeSlot.CONTENT) { content(windowWidth, windowSize, kind, chromeSize) }
            .single()
            .measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Placed relatively, so that the rail sits on the start edge the screens are inset from rather than always
            // on the left one.
            if (isChromePlaced) {
                if (outgoingPlaceable != null) {
                    placeChrome(outgoingPlaceable, outgoingKind, otherKind = kind, visibility = 1f - progress, windowHeight = constraints.maxHeight)
                }
                placeChrome(
                    chromePlaceable,
                    kind,
                    otherKind = outgoingKind,
                    visibility = if (outgoingKind == null) 1f else progress,
                    windowHeight = constraints.maxHeight
                )
            }
            contentPlaceable.placeRelative(x = 0, y = 0)
        }
    }
}

/**
 * Puts one of the two chromes of [NavigationChromeScaffold] on its edge, [visibility] of the way faded in. A chrome
 * that is handing over to or from one on the other edge also slides in from that edge as it fades - a bar and a rail
 * come from different directions - while two rails share an edge and only fade into each other.
 */
private fun Placeable.PlacementScope.placeChrome(
    placeable: Placeable,
    kind: NavigationChromeKind,
    otherKind: NavigationChromeKind?,
    visibility: Float,
    windowHeight: Int,
) {
    val slide = if (otherKind != null && otherKind.isRail != kind.isRail) 1f - visibility else 0f
    if (kind.isRail) {
        placeable.placeRelativeWithLayer(x = -(placeable.width * slide).roundToInt(), y = 0) { alpha = visibility }
    } else {
        placeable.placeRelativeWithLayer(x = 0, y = windowHeight - placeable.height + (placeable.height * slide).roundToInt()) { alpha = visibility }
    }
}

private enum class ChromeSlot { CHROME, OUTGOING_CHROME, CONTENT }

/**
 * The three shapes the navigation chrome takes: the bar under 600dp, the rail above, and the expanded rail - the one
 * with each label beside its icon rather than under it - where the window has the room for it. That rail is well over
 * a hundred dp wider than the collapsed one, and the lists next to it are what pays for it, so it is only used where
 * the list screens still keep their filter side panel beside it (see [navigationChromeKind]).
 */
private enum class NavigationChromeKind {
    BAR,
    RAIL,
    EXPANDED_RAIL;

    val isRail get() = this != BAR
}

/**
 * What the navigation chrome takes out of the window: [railWidth] and [barHeight] as they are on this frame - one of
 * them nothing, and both something while the chrome changes shape - and [settledRailWidth], what the rail will take
 * once it has arrived. Anything a screen has to decide once is decided from the settled one, so that the column
 * counts do not change a dozen times as the chrome moves.
 */
private data class NavigationChromeSize(
    val railWidth: Dp,
    val barHeight: Dp,
    val settledRailWidth: Dp,
)

/**
 * Whether a window this wide has the room for the expanded navigation rail. Deciding it from anything but the list
 * screens' own side panel would have the panel come, go and come again as a window is widened past both thresholds.
 *
 * Material decides the expanded rail's width from its items, [EXPANDED_NAVIGATION_RAIL_MIN_WIDTH] being where it
 * starts; the four labels here fit inside that in every language the app speaks.
 */
private fun navigationChromeKind(windowWidth: Dp) = when {
    !WindowSize.fromWidth(windowWidth).usesNavigationRail -> NavigationChromeKind.BAR
    hasRoomForSidePanel(windowWidth - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH) -> NavigationChromeKind.EXPANDED_RAIL
    else -> NavigationChromeKind.RAIL
}

/**
 * Where [NavigationChromeScaffold] is in handing the window from one [NavigationChromeKind] to another. Plain fields
 * where only the layout reads them, state where the composition does (the running number that restarts the animation)
 * or where a change has to lay the scaffold out again (the progress, the chrome on its way out): the scaffold writes
 * to it from its measure block, since that is where the window's width first becomes known.
 *
 * The sizes are in pixels, the chrome being measured there, and [progress] is how far the room the chrome takes has
 * come from the `from` sizes, what was on screen when the kind changed, to the `to` ones.
 */
private class NavigationChromeTransition {
    private var kind: NavigationChromeKind? = null
    var fromRailWidth = 0f
        private set
    var fromBarHeight = 0f
        private set
    var toRailWidth = 0f
        private set
    var toBarHeight = 0f
        private set
    var progress by mutableFloatStateOf(1f)
    var outgoingKind by mutableStateOf<NavigationChromeKind?>(null)
    var generation by mutableIntStateOf(0)
        private set

    /** Takes the chrome measured for the window on this frame; a change of [kind] starts the handover. */
    fun update(kind: NavigationChromeKind, railWidth: Int, barHeight: Int) {
        val previousKind = this.kind
        if (previousKind != null && previousKind != kind) {
            // From what is on screen, which an interrupted handover has not finished getting to.
            fromRailWidth = lerp(fromRailWidth, toRailWidth, progress)
            fromBarHeight = lerp(fromBarHeight, toBarHeight, progress)
            progress = 0f
            outgoingKind = previousKind
            generation++
        }
        this.kind = kind
        toRailWidth = railWidth.toFloat()
        toBarHeight = barHeight.toFloat()
        if (previousKind == null) {
            fromRailWidth = toRailWidth
            fromBarHeight = toBarHeight
        }
    }
}

private val EXPANDED_NAVIGATION_RAIL_MIN_WIDTH = 220.dp

/** Material adds the icon and its padding to this reserved label width, giving every item a 180dp pill. */
private val EXPANDED_NAVIGATION_RAIL_LABEL_WIDTH = 116.dp

/** The weight of a navigation bar item that has all but left, since a weight of zero is refused. */
private const val MIN_NAVIGATION_ITEM_WEIGHT = 0.001f

/** The gap the collapsed [NavigationRail] leaves above its first item. */
private val EXPANDED_NAVIGATION_RAIL_TOP_PADDING = 4.dp

/**
 * The navigation bar (under 600dp), navigation rail or expanded navigation rail (see [navigationChromeKind]) that
 * every top level screen shares. It belongs to the bottom of the deck rather than to any one screen: it is laid out
 * once for the window and stays there while the tabs fade through in place next to it. A card dealt over the deck
 * moves the screen under it a little, and the chrome is part of that screen as far as the eye can tell, so for as long
 * as a card covers the deck or is being taken off it every top level screen draws a copy of it instead (see
 * [CampfireScreens]), which moves with the screen and which the card covers.
 *
 * @param destinations The top level screens of the features switched on, see `CampfireViewModel.topLevelDestinations`.
 *   The item of one that is not is disabled while it shrinks away, so that it takes no tap and no focus and is not
 *   announced, and drawn in its unselected colors when disabled, since a disabled item is dimmed - in one frame on the
 *   wide rail - and nothing but the shrink should change on screen.
 * @param metronomeBeat Where the Metronome item's icon is in the beat, see [rememberMetronomeIconBeat]: one state for
 *   every copy of the chrome, so that a copy drawn under a card moves in step with the shared one.
 */
@Composable
private fun NavigationChrome(
    kind: NavigationChromeKind,
    destinations: List<CampfireDestination.TopLevel>,
    currentTopLevelDestination: CampfireDestination.TopLevel?,
    metronomeBeat: MetronomeIconBeat,
    onDestinationSelected: (CampfireDestination.TopLevel) -> Unit,
) {
    if (kind == NavigationChromeKind.EXPANDED_RAIL) {
        // The wide rail's own collapsed state is not used: its collapsed form is wider than the plain rail, and the
        // window width alone decides which of the two a window gets, the scaffold handing one over to the other. The
        // state is only ever the expanded one.
        WideNavigationRail(
            state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
            windowInsets = WideNavigationRailDefaults.windowInsets.withMinTopEdge,
            // The default leaves room above the items for a header this rail does not have, which would drop them
            // 40dp lower than the collapsed rail's as the window crosses from the one to the other.
            contentPadding = PaddingValues(top = EXPANDED_NAVIGATION_RAIL_TOP_PADDING),
        ) {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationItemPresence(isShown = destination in destinations) { presence ->
                    WideNavigationRailItem(
                        modifier = Modifier.collapsingNavigationItem(presence = presence, isHorizontal = false),
                        selected = destination == currentTopLevelDestination,
                        onClick = { onDestinationSelected(destination) },
                        enabled = destination in destinations,
                        colors = WideNavigationRailItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { DestinationIcon(destination = destination, metronomeBeat = metronomeBeat) },
                        label = {
                            Text(
                                text = stringResource(destination.label),
                                modifier = Modifier.width(EXPANDED_NAVIGATION_RAIL_LABEL_WIDTH),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        railExpanded = true,
                    )
                }
            }
        }
    } else if (kind == NavigationChromeKind.RAIL) {
        NavigationRail(windowInsets = NavigationRailDefaults.windowInsets.withMinTopEdge) {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationItemPresence(isShown = destination in destinations) { presence ->
                    NavigationRailItem(
                        modifier = Modifier.collapsingNavigationItem(presence = presence, isHorizontal = false),
                        selected = destination == currentTopLevelDestination,
                        onClick = { onDestinationSelected(destination) },
                        enabled = destination in destinations,
                        colors = NavigationRailItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { DestinationIcon(destination = destination, metronomeBeat = metronomeBeat) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        }
    } else {
        NavigationBar {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationItemPresence(isShown = destination in destinations) { presence ->
                    NavigationBarItem(
                        // The bar hands its width out by weight, and the outermost weight is the one it reads, so this
                        // one rather than the item's own decides the slot. A weight cannot be zero.
                        modifier = Modifier
                            .weight(presence().coerceAtLeast(MIN_NAVIGATION_ITEM_WEIGHT))
                            .collapsingNavigationItem(presence = presence, isHorizontal = true),
                        selected = destination == currentTopLevelDestination,
                        onClick = { onDestinationSelected(destination) },
                        enabled = destination in destinations,
                        colors = NavigationBarItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { DestinationIcon(destination = destination, metronomeBeat = metronomeBeat) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        }
    }
}

/**
 * One card of the deck that covers the navigation chrome: an opaque screen over the whole window. The screens have to
 * be opaque, otherwise the one being covered would show through the one covering it.
 *
 * A [Surface] rather than a plain box because it also blocks touches from reaching what is behind it: the chrome is
 * drawn under the screens, so without this the rail would still take taps through the song details screen covering
 * it, and a screen being covered would still take taps through the one landing on it.
 *
 * It also takes no touches while it is moving - being dealt, taken away, or uncovered by the card above it leaving.
 * Navigation 3 holds every entry below RESUMED until its scene transition has settled, so an entry below RESUMED in a
 * host that is RESUMED is one that is moving. The editor leaves on a spring that has cleared a finger within a tenth of
 * a second, and without this the second tap of a double-tap on its Close would land on the Back arrow of the song
 * underneath and close that too. The host is asked as well because it is not always RESUMED while the app is in use:
 * a desktop window that does not have the focus is only STARTED, and the click that focuses it has to count.
 */
@Composable
private fun ScreenSurface(
    hostLifecycle: Lifecycle,
    scrim: NavigationScrim,
    content: @Composable () -> Unit,
) {
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    val scrimCoverage = rememberScrimCoverage(scrim)
    val scrimColor = MaterialTheme.colorScheme.scrim
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .coveredScreenScrim(scrimColor) { scrimCoverage.value }
            .pointerInput(hostLifecycle, entryLifecycle) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Asked when the finger comes down rather than for every event: a gesture that started on a screen
                    // that had landed is the user's to finish, and one that started on a moving screen is not, even if
                    // the screen lands before the finger is lifted. The down is consumed as well, or a child that does
                    // not require an unconsumed down would still start its press ripple.
                    if (hostLifecycle.currentState == Lifecycle.State.RESUMED && !entryLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/**
 * One card of the deck next to the navigation chrome, inset from the navigation rail or bar rather than clipped, so
 * that the chrome stays visible beside it. Given a [chrome], it draws that under itself where
 * [NavigationChromeScaffold] places the shared one, so that nothing moves as the one hands over to the other.
 *
 * A [Surface], so that it blocks touches from reaching the screen it covers during a transition, and so that it keeps
 * its screen below the status bar: none of the top level screens has a top app bar of its own to do that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopLevelScreenSurface(
    scrim: NavigationScrim,
    windowSize: WindowSize,
    railWidth: Dp,
    navigationBarHeight: Dp,
    chrome: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val scrimCoverage = rememberScrimCoverage(scrim)
    val scrimColor = MaterialTheme.colorScheme.scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .coveredScreenScrim(scrimColor) { scrimCoverage.value },
    ) {
        if (chrome != null) {
            Box(
                modifier = Modifier.align(if (windowSize.usesNavigationRail) Alignment.TopStart else Alignment.BottomStart),
            ) {
                chrome()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = railWidth, bottom = navigationBarHeight)
                // The chrome covers the insets on its own edge, so nothing inside should apply them a second time.
                .consumeWindowInsets(PaddingValues(start = railWidth, bottom = navigationBarHeight)),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Top).withMinTopEdge)) {
                content()
            }
        }
    }
}

/**
 * The screens are a deck of cards. Pushing one deals it over the top of the deck: it slides in from the end while
 * the screen it lands on gives way in the same direction over a small fraction of the distance. Popping takes the
 * top card off again: it slides back out and the screen underneath returns from that short offset. The screen
 * underneath moves as one piece, its navigation chrome included, so it keeps whatever it had on screen (its scroll
 * position, the caret in its search field) in the same place relative to itself across the whole transition.
 *
 * Switching between top level destinations is not a deal but a swap of the bottom card, so those fade through in
 * place, next to a navigation bar and a navigation rail alike: nothing about two tabs puts one of them in any
 * direction of the other.
 *
 * Whether a transition is a push or a pop is decided here from the depth of the scenes instead of relying on
 * Navigation 3's own detection: when a back stack change interrupts a running transition, Navigation 3 records the
 * already updated back stack as the transition's starting point and animates a pop with the push spec. That leaves
 * the outgoing screen invisible but still covering (and swallowing clicks on) the screen underneath until the
 * animation ends. The same specs are used on every platform (the desktop default would be no animation at all).
 *
 * The decision is also handed to [scrim], which darkens the screen underneath for as long as a card covers any of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.navigationTransition(
    motionScheme: MotionScheme,
    scrim: NavigationScrim,
): ContentTransform {
    val from = CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey)
    val to = CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey)
    scrim.isPredictiveBack = false
    scrim.motion = when {
        from != null && to != null -> DeckMotion.None
        targetState.zIndex < initialState.zIndex -> DeckMotion.Pop
        else -> DeckMotion.Push
    }
    return when (scrim.motion) {
        DeckMotion.None -> tabTransition()
        DeckMotion.Pop, DeckMotion.PredictivePop -> popTransition(motionScheme)
        DeckMotion.Push -> pushTransition(motionScheme)
    }
}

/** What the back stack changes with while the launch screen still covers the app: nothing moves, nothing is darkened. */
private fun instantTransition(scrim: NavigationScrim): ContentTransform {
    scrim.isPredictiveBack = false
    scrim.motion = DeckMotion.None
    return ContentTransform(EnterTransition.None, ExitTransition.None)
}

/**
 * The card being dealt slides in over the deck. The screen underneath follows in the same direction over a much
 * shorter distance. [ExitTransition.KeepUntilTransitionsFinished] keeps it drawn until the card has landed.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.pushTransition(
    motionScheme: MotionScheme,
): ContentTransform {
    val direction = AnimatedContentTransitionScope.SlideDirection.Left
    val spec = motionScheme.slideSpec()
    return ContentTransform(
        targetContentEnter = slideIntoContainer(towards = direction, animationSpec = spec),
        initialContentExit = slideOutOfContainer(
            towards = direction,
            animationSpec = spec,
            targetOffset = ::backgroundSlideOffset,
        ) + ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = targetState.zIndex,
    )
}

/**
 * The top card slides away while the screen underneath follows it from a shorter offset. The z indices keep the
 * card that is leaving above the screen it reveals.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.popTransition(
    motionScheme: MotionScheme,
): ContentTransform {
    val direction = AnimatedContentTransitionScope.SlideDirection.Right
    val spec = motionScheme.slideSpec()
    return ContentTransform(
        targetContentEnter = slideIntoContainer(
            towards = direction,
            animationSpec = spec,
            initialOffset = ::backgroundSlideOffset,
        ),
        initialContentExit = slideOutOfContainer(towards = direction, animationSpec = spec),
        targetContentZIndex = targetState.zIndex,
    )
}

/** The underlying screen travels a small fraction of the card's full slide, using the same animation progress. */
private fun backgroundSlideOffset(fullSlideOffset: Int) = (fullSlideOffset * BACKGROUND_SLIDE_FRACTION).roundToInt()

/**
 * The theme's default spatial spring, made to settle once the card is within a pixel of where it lands. The theme's
 * spring carries no visibility threshold, so on an [IntOffset] it runs on to a hundredth of a pixel: a screen that
 * has not moved a whole pixel for the last third of a second is still counted as moving, and [ScreenSurface] takes
 * no taps until it is not. An offset is drawn in whole pixels, so nothing past this one is ever seen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MotionScheme.slideSpec() = when (val spec = defaultSpatialSpec<IntOffset>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = IntOffset.VisibilityThreshold)
    else -> spec
}

/**
 * [slideSpec] for a slide animated as the fraction of the width it has covered rather than as an offset, which keeps
 * the two on the same curve. It settles within a thousandth of the width, a pixel or less on any window it is seen in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MotionScheme.slideFractionSpec() = when (val spec = defaultSpatialSpec<Float>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = SLIDE_FRACTION_THRESHOLD)
    else -> spec
}

/**
 * The pop driven by the predictive back gesture (Android) or the edge swipe (iOS): the same uncovering as
 * [popTransition], except that both screens follow the finger with linear specs. Their direction is fixed to the
 * horizontal pop, regardless of which edge the gesture started from.
 *
 * Going back from Setlists or Settings to Songs is a swap of tabs rather than a card being taken off, so it cross
 * fades in place, for the reasons [navigationTransition] gives. It is a cross fade rather than [tabTransition]'s fade
 * through, since the gesture can be held anywhere along its way, and a fade through would show neither screen for
 * the middle of it.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.predictivePopTransition(scrim: NavigationScrim): ContentTransform {
    scrim.isPredictiveBack = true
    if (CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey) != null &&
        CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey) != null
    ) {
        scrim.motion = DeckMotion.None
        val spec = tween<Float>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
        return ContentTransform(
            targetContentEnter = fadeIn(spec),
            initialContentExit = fadeOut(spec),
            targetContentZIndex = targetState.zIndex,
        )
    }
    scrim.motion = DeckMotion.PredictivePop
    val towards = AnimatedContentTransitionScope.SlideDirection.Right
    val spec = tween<IntOffset>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
    return ContentTransform(
        targetContentEnter = slideIntoContainer(towards, spec, initialOffset = ::backgroundSlideOffset),
        initialContentExit = slideOutOfContainer(towards, spec),
        targetContentZIndex = targetState.zIndex,
    )
}

/**
 * Material's fade through: the screen being left fades out first and only then does the one being opened fade in,
 * rather than the two cross fading at the same time. Two lists half drawn over each other read as a smear of
 * overlapping text for the middle of a cross fade, whereas this passes through nothing but the background both
 * screens are painted on.
 */
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.tabTransition() = ContentTransform(
    targetContentEnter = fadeIn(tween(TAB_FADE_IN_DURATION, delayMillis = TAB_FADE_OUT_DURATION, easing = LinearOutSlowInEasing)),
    initialContentExit = fadeOut(tween(TAB_FADE_OUT_DURATION, easing = FastOutLinearInEasing)),
    targetContentZIndex = targetState.zIndex,
)

/** How the deck is changing in the transition that is running, which decides which of its two screens is darkened. */
private enum class DeckMotion {
    /** A tab swap, or nothing animated at all: neither screen is under the other, so neither is darkened. */
    None,

    /** A card is dealt over the screen being left, which darkens as it is covered. */
    Push,

    /** The top card is taken off the screen being returned to, which starts darkened and clears as it is uncovered. */
    Pop,

    /** [Pop], following the back gesture rather than a spring. */
    PredictivePop,
}

/**
 * What the transition specs tell every entry's scrim about the transition they have just decided on. The specs are
 * evaluated in the composition of each screen of a transition before that screen's own content, so the screen reads
 * the motion of the transition it is part of, an interrupted one included.
 */
@Stable
private class NavigationScrim {
    var motion by mutableStateOf(DeckMotion.None)

    /**
     * Whether the transition is following a back gesture, a tab swap's included. Read when the gesture completes, which
     * is when the specs have last been asked about it, and plain rather than snapshot state, since nothing draws it.
     */
    var isPredictiveBack = false
}

/**
 * How much of a dialog's scrim the screen this is called from is under, from 0 to 1. Only the screen underneath a
 * moving card is ever darkened: fully once a card has been dealt over it, and from fully to not at all as the card is
 * taken off it, in step with the card's slide - the same spring, or the back gesture's linear progress, which the
 * transition seeks this along with the slides. The card on top, and either screen of a tab swap, stay clear.
 *
 * Animated on the screen's own enter and exit transition, so it settles exactly when the screen does and is read
 * only while drawing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberScrimCoverage(scrim: NavigationScrim): State<Float> {
    val slideSpec = MaterialTheme.motionScheme.slideFractionSpec()
    return LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = { if (scrim.motion == DeckMotion.PredictivePop) tween(PREDICTIVE_BACK_DURATION, easing = LinearEasing) else slideSpec },
        label = "navigationScrim",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> if (scrim.motion == DeckMotion.Pop || scrim.motion == DeckMotion.PredictivePop) 1f else 0f
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> if (scrim.motion == DeckMotion.Push) 1f else 0f
        }
    }
}

/**
 * Draws a dialog's scrim over everything this draws, as dark as [coverage] says it is covered. Clamped, since the
 * spring it follows can overshoot either end.
 */
private fun Modifier.coveredScreenScrim(color: Color, coverage: () -> Float) = drawWithContent {
    drawContent()
    val alpha = coverage().coerceIn(0f, 1f) * COVERED_SCREEN_SCRIM_ALPHA
    if (alpha > 0f) {
        drawRect(color = color, alpha = alpha)
    }
}

/**
 * Deeper screens are drawn above shallower ones, so that a pushed screen covers its parent and a popped screen
 * slides away on top of the screen it reveals.
 */
private val Scene<CampfireDestination>.zIndex: Float
    get() = previousEntries.size.toFloat()

/** The icon of a navigation item, the Metronome's swinging and pulsing with the click. */
@Composable
private fun DestinationIcon(
    destination: CampfireDestination.TopLevel,
    metronomeBeat: MetronomeIconBeat,
) = when (destination) {
    CampfireDestination.Songs -> Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null)
    CampfireDestination.Setlists -> Icon(painter = painterResource(Res.drawable.ic_setlists), contentDescription = null)
    CampfireDestination.Metronome -> MetronomeIcon(beat = metronomeBeat, contentDescription = null)
    CampfireDestination.Settings -> Icon(painter = painterResource(Res.drawable.ic_settings), contentDescription = null)
}

private val CampfireDestination.TopLevel.label: StringResource
    get() = when (this) {
        CampfireDestination.Songs -> Res.string.songs
        CampfireDestination.Setlists -> Res.string.setlists
        CampfireDestination.Metronome -> Res.string.metronome
        CampfireDestination.Settings -> Res.string.settings
    }

/**
 * Every entry reports whether the [NavDisplay] transition hosting it is running, so that the view model knows when
 * a back stack change interrupts an animation (see [CampfireViewModel.navigationGeneration]), and so that
 * [CampfireContent] knows when a pop has settled and the navigation chrome can leave the top level screens again.
 */
@Composable
private fun ReportNavigationTransition(
    viewModel: CampfireViewModel,
    onRunningChanged: (Boolean) -> Unit,
) {
    val isRunning = LocalNavAnimatedContentScope.current.transition.isRunning
    SideEffect {
        viewModel.setNavigationTransitionRunning(isRunning)
        onRunningChanged(isRunning)
    }
}

private val LAUNCH_MARK_SIZE = 72.dp

/**
 * How much the mark has grown by the time it has faded away, on the platform that watches it leave: half as large
 * again, which is enough of a movement to be seen for what it is over the length of the fade rather than read as
 * the picture drifting.
 */
private const val LAUNCH_MARK_EXIT_GROWTH = 0.5f
private const val NAVIGATION_GENERATION_METADATA_KEY = "navigationGeneration"
private const val TAB_FADE_OUT_DURATION = 90
private const val TAB_FADE_IN_DURATION = 210
private const val PREDICTIVE_BACK_DURATION = 350
private const val BACKGROUND_SLIDE_FRACTION = 0.12f

/** Material's scrim behind a dialog or a modal sheet, which is what a fully covered screen is darkened to. */
private const val COVERED_SCREEN_SCRIM_ALPHA = 0.32f
private const val SLIDE_FRACTION_THRESHOLD = 0.001f

/** How many of the files an export left out its message names, the rest being counted rather than listed. */
private const val MAXIMUM_NAMED_FILES = 3

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
            viewModel.metronomeBeats.collect { beat ->
                if (!beat.isSubdivision && beat.level != BeatLevel.MUTED) haptics.onBeat(isAccent = beat.level == BeatLevel.ACCENT)
            }
        }
    }
}
