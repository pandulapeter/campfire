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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.error_link_not_opened
import com.pandulapeter.campfire.presentation.resources.error_operation_failed
import com.pandulapeter.campfire.presentation.resources.export_failed
import com.pandulapeter.campfire.presentation.resources.export_skipped_files
import com.pandulapeter.campfire.presentation.resources.export_too_large_to_import
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_oversized
import com.pandulapeter.campfire.presentation.resources.import_result
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_lost
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_restored
import com.pandulapeter.campfire.presentation.resources.song_editor_file_gone
import com.pandulapeter.campfire.presentation.resources.song_editor_save_failed
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_sync_cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_channel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_partly
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name_partly
import com.pandulapeter.campfire.presentation.ui.components.TopLevelScreenLayout
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.pluralTextResource
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.areDrawablesLoaded
import com.pandulapeter.campfire.presentation.ui.platform.isLaunchScreenWholeStartup
import com.pandulapeter.campfire.presentation.ui.platform.libraryLocation
import com.pandulapeter.campfire.presentation.ui.dialogs.CampfireDialogs
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistsScreen
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsScreen
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDetailsScreen
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.SongEditorScreen
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongsScreen
import com.pandulapeter.campfire.presentation.ui.theme.ApplyLanguagePreference
import com.pandulapeter.campfire.presentation.ui.theme.CampfireTheme
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel

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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CampfireApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    onAppReady: () -> Unit = {},
) {
    LaunchedEffect(filesToImport) { filesToImport.collect(viewModel::importFiles) }
    SyncNotificationEffect(viewModel)
    // A library the user can reach from outside the app (the desktop folder, the iOS Files app) can also change
    // while the app is away, so it is read again whenever Campfire comes back to the front. One only the app can
    // see - Android's private storage, the browser's origin private file system - cannot change behind its back,
    // and re-reading every song on each window focus would be cost with nothing to show for it.
    //
    // The first resume is the one that follows the initial load, and is skipped.
    if (libraryLocation != null) {
        var hasResumedBefore by remember { mutableStateOf(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            if (hasResumedBefore) viewModel.refresh() else hasResumedBefore = true
        }
    }
    // The editor's unsaved text goes to disk whenever the app stops being the one in front, see
    // CampfireViewModel.onAppPaused. ON_PAUSE rather than ON_STOP: it always comes first, and an app swiped away from
    // iOS's app switcher may never have got any further.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAppPaused() }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val arePreferencesLoaded by viewModel.arePreferencesLoaded.collectAsStateWithLifecycle()
    val hasLibraryToShow by viewModel.hasLibraryToShow.collectAsStateWithLifecycle()
    ApplyLanguagePreference(userPreferences?.language)
    CampfireTheme(
        uiMode = userPreferences?.uiMode,
        themeColor = userPreferences?.themeColor,
    ) { isThemeSettled ->
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
                // Inside the theme, so that the one screen it can put in the way of the app is drawn in the colors
                // the user chose, and above the language preference, so that it is in the language they chose too.
                AppUpdateGate(viewModel = viewModel) {
                    CampfireContent(
                        viewModel = viewModel,
                        urlOpener = urlOpener,
                    )
                }
            }
            // The launch screen covers the app rather than standing in for it, and it fades away once there is
            // something to look at underneath: the app composes, lays out and draws behind it in the meantime, so
            // holding it through the first read of the library costs none of the time that read was going to take
            // anyway. What it covers is the handful of frames the song list would otherwise open on its loading
            // indicator for, and a startup screen handing over to a spinner is two startup screens in a row - the
            // very thing onAppReady exists to keep the other shells from doing.
            //
            // The colors have to have stopped moving as well, or the app would be uncovered halfway through the
            // cross fade that corrects the theme the window opened on, which is the one thing the launch screen is
            // there to take instead of it.
            //
            // Read once: the composition that took the launch screen away is not always the one drawing the app
            // (Android recreates its activity on every configuration change), and one that starts after it has
            // nothing to cover.
            val hasShownAppBefore = remember { viewModel.hasShownApp }
            var isAppReady by remember { mutableStateOf(hasShownAppBefore) }
            if (hasShownAppBefore) {
                // The shell still holds a startup screen of its own until it is told, the Android activity's pre-draw
                // gate.
                LaunchedEffect(Unit) { onAppReady() }
            }
            if (!isAppReady) {
                val opacity = remember { Animatable(1f) }
                // In the desktop application the mark grows as it goes, over the slower of the two effect springs so
                // that the movement has the time to be read as one: this is the one platform where the launch screen is
                // the whole of the startup - the first frame the window paints and the last one before the app - so it
                // is worth leaving by opening into the app rather than by merely thinning out. The other three
                // open on a startup screen of their own and never watch this one go: Android's splash and the web's
                // loading page cover the fade entirely, and on iOS the mark is already the second thing shown - so
                // there it stays the plain, quicker dissolve, and the extra frames are not spent.
                val markGrowth = if (isLaunchScreenWholeStartup) LAUNCH_MARK_EXIT_GROWTH else 0f
                LaunchScreen(
                    modifier = Modifier.graphicsLayer { alpha = opacity.value.coerceIn(0f, 1f) },
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
                        // Two frames rather than one, because withFrameNanos resumes while the frame it belongs to
                        // is still being assembled: the frame after it is the first one that is certainly drawn.
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
 * fading, when the app can already be seen through it but is still nobody's to touch.
 *
 * @param markScale How large the mark is drawn, read in the layer rather than in the composition so that animating
 *   it is a redraw instead of a recomposition of the whole screen.
 */
@Composable
private fun LaunchScreen(
    modifier: Modifier = Modifier,
    markScale: () -> Float = { 1f },
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier
                .size(LAUNCH_MARK_SIZE)
                .graphicsLayer {
                    scaleX = markScale()
                    scaleY = markScale()
                },
            painter = painterResource(Res.drawable.ic_campfire),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val backStack = viewModel.backStack
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

    NavigationChromeScaffold(
        // Painted here as well as on every screen, so that the two screens of a cross fading tab transition blend into
        // the same color they are painted in and the fade stays invisible.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        isChromePlaced = !chromeInScreens,
        chrome = { windowSize, isNavigationRailExpanded ->
            NavigationChrome(
                windowSize = windowSize,
                isNavigationRailExpanded = isNavigationRailExpanded,
                currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel,
                onDestinationSelected = viewModel::selectTopLevelDestination,
            )
        },
    ) { windowWidth, windowSize, isNavigationRailExpanded, chromeThickness ->
        CampfireScreens(
            viewModel = viewModel,
            urlOpener = urlOpener,
            windowWidth = windowWidth,
            windowSize = windowSize,
            isNavigationRailExpanded = isNavigationRailExpanded,
            chromeThickness = chromeThickness,
            chromeInScreens = chromeInScreens,
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
    isNavigationRailExpanded: Boolean,
    chromeThickness: Dp,
    chromeInScreens: Boolean,
    onNavigationTransitionRunningChanged: (Boolean) -> Unit,
) {
    val backStack = viewModel.backStack
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    // Makes interrupted transitions retarget instead of getting stuck, see CampfireViewModel.navigationGeneration.
    val navigationMetadata = mapOf(NAVIGATION_GENERATION_METADATA_KEY to viewModel.navigationGeneration)

    val railWidth = if (windowSize.usesNavigationRail) chromeThickness else 0.dp
    val navigationBarHeight = if (windowSize.usesNavigationRail) 0.dp else chromeThickness

    // The screens next to the chrome always settle at the width the chrome leaves them, whether or not one of them
    // happens to be covered right now; the song details screen covers the chrome, so it settles at the full width.
    // Anything a screen has to decide once, before it is first drawn, is decided from these rather than from the
    // width it is being measured at: the column counts of the song lists and the lyrics, and whether the lists have
    // room for their filter side panel.
    val settledListWidth = windowWidth - railWidth
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
    val songDetailsContentPadding: PaddingValues = KeyboardAwarePadding(
        start = systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        bottom = systemBars.calculateBottomPadding(),
        coveredHeight = 0.dp,
        ime = ime,
        density = density,
    )
    // The snackbar sits above the chrome, and above the keyboard wherever that reaches higher: the app is laid out
    // under the keyboard rather than resized by it, and a message sent while somebody is typing - a save that failed
    // in the editor - would otherwise time out behind it unseen.
    val messagesPadding: PaddingValues = KeyboardAwarePadding(
        start = 0.dp,
        end = 0.dp,
        bottom = navigationBarHeight + systemBars.calculateBottomPadding(),
        coveredHeight = 0.dp,
        ime = ime,
        density = density,
    )
    val screenChrome: (CampfireDestination.TopLevel) -> (@Composable () -> Unit)? = { destination ->
        if (chromeInScreens) {
            {
                NavigationChrome(
                    windowSize = windowSize,
                    isNavigationRailExpanded = isNavigationRailExpanded,
                    currentTopLevelDestination = destination,
                    onDestinationSelected = viewModel::selectTopLevelDestination,
                )
            }
        } else {
            null
        }
    }
    // What the screens' own lifecycles are compared with, see ScreenSurface.
    val hostLifecycle = LocalLifecycleOwner.current.lifecycle

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = backStack,
            onBack = viewModel::navigateBack,
            // The same spec decides the direction for both parameters, see navigationTransition. Nothing is animated
            // while the launch screen still covers the app: a place the app was asked to open on (the web build's
            // address, Settings after a consent page) is put on the stack behind it, and a screen still sliding in as
            // the launch screen fades would be the app arriving twice.
            transitionSpec = { if (viewModel.hasShownApp) navigationTransition(motionScheme) else ContentTransform(EnterTransition.None, ExitTransition.None) },
            popTransitionSpec = { if (viewModel.hasShownApp) navigationTransition(motionScheme) else ContentTransform(EnterTransition.None, ExitTransition.None) },
            predictivePopTransitionSpec = { predictivePopTransition() },
            // Stable string content keys, so that the transitions can recognize the top level destinations.
            entryProvider = entryProvider {
                entry<CampfireDestination.Songs>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        windowSize = windowSize,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        SongsScreen(
                            viewModel = viewModel,
                            settledWidth = settledListWidth,
                            railWidth = railWidth,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Setlists>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        windowSize = windowSize,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        SetlistsScreen(
                            viewModel = viewModel,
                            settledWidth = settledListWidth,
                            railWidth = railWidth,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Settings>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    TopLevelScreenSurface(
                        windowSize = windowSize,
                        navigationBarHeight = navigationBarHeight,
                        chrome = screenChrome(destination),
                    ) {
                        SettingsScreen(
                            viewModel = viewModel,
                            settledWidth = settledListWidth,
                            railWidth = railWidth,
                            contentPadding = shellContentPadding,
                            urlOpener = urlOpener,
                        )
                    }
                }
                // These two cover the chrome, so they are the only ones laid out edge to edge.
                entry<CampfireDestination.SongEditor>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    ScreenSurface(hostLifecycle) {
                        SongEditorScreen(
                            viewModel = viewModel,
                            destination = destination,
                            windowSize = windowSize,
                            contentPadding = songDetailsContentPadding,
                            onBack = viewModel::navigateBack,
                        )
                    }
                }
                entry<CampfireDestination.SongDetails>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel, onNavigationTransitionRunningChanged)
                    ScreenSurface(hostLifecycle) {
                        SongDetailsScreen(
                            viewModel = viewModel,
                            destination = destination,
                            windowSize = windowSize,
                            settledWidth = settledSongDetailsWidth,
                            contentPadding = songDetailsContentPadding,
                            onBack = viewModel::navigateBack,
                        )
                    }
                }
            },
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
        is CampfireViewModel.Message.ImportFinished -> stringResource(
            Res.string.import_result,
            current.result.importedSongFileNames.size,
            current.result.importedSetlistFileNames.size,
            current.result.duplicateFileNames.size,
            current.result.skippedFileNames.size,
        )

        is CampfireViewModel.Message.ImportOversized -> pluralStringResource(Res.plurals.import_oversized, current.count, current.count)
        CampfireViewModel.Message.ImportFailed -> stringResource(Res.string.import_failed)
        CampfireViewModel.Message.ExportFailed -> stringResource(Res.string.export_failed)
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
        null -> null
    }
    LaunchedEffect(head?.index) {
        if (head != null && text != null) {
            snackbarHostState.showSnackbar(text)
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
 * Lays the navigation chrome out and hands [content] the size of the window along with the thickness the chrome
 * takes out of it, all in one pass.
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
 */
@Composable
private fun NavigationChromeScaffold(
    modifier: Modifier = Modifier,
    isChromePlaced: Boolean,
    chrome: @Composable (windowSize: WindowSize, isNavigationRailExpanded: Boolean) -> Unit,
    content: @Composable (windowWidth: Dp, windowSize: WindowSize, isNavigationRailExpanded: Boolean, chromeThickness: Dp) -> Unit,
) = SubcomposeLayout(modifier) { constraints ->
    val windowWidth = constraints.maxWidth.toDp()
    val windowSize = WindowSize.fromWidth(windowWidth)
    val isNavigationRailExpanded = isNavigationRailExpanded(windowWidth)
    // Loose constraints, so that the rail and the bar each take only the one dimension they want.
    val chromePlaceable = subcompose(ChromeSlot.CHROME) { chrome(windowSize, isNavigationRailExpanded) }
        .single()
        .measure(constraints.copy(minWidth = 0, minHeight = 0))
    val chromeThickness = if (windowSize.usesNavigationRail) chromePlaceable.width else chromePlaceable.height
    val contentPlaceable = subcompose(ChromeSlot.CONTENT) { content(windowWidth, windowSize, isNavigationRailExpanded, chromeThickness.toDp()) }
        .single()
        .measure(constraints)
    layout(constraints.maxWidth, constraints.maxHeight) {
        // Placed relatively, so that the rail sits on the start edge the screens are inset from rather than always
        // on the left one.
        if (isChromePlaced) {
            chromePlaceable.placeRelative(
                x = 0,
                y = if (windowSize.usesNavigationRail) 0 else constraints.maxHeight - chromePlaceable.height,
            )
        }
        contentPlaceable.placeRelative(x = 0, y = 0)
    }
}

private enum class ChromeSlot { CHROME, CONTENT }

/**
 * Whether a window this wide has the room for the expanded navigation rail, the one with each label beside its icon
 * rather than under it. That rail is well over a hundred dp wider than the collapsed one, and the lists next to it are
 * what pays for it, so it is only used where the list screens still keep their filter side panel beside it. Deciding
 * it from anything else would have the panel come, go and come again as a window is widened past both thresholds.
 *
 * Material decides the expanded rail's width from its items, [EXPANDED_NAVIGATION_RAIL_MIN_WIDTH] being where it
 * starts; the three labels here fit inside that in every language the app speaks.
 */
private fun isNavigationRailExpanded(windowWidth: Dp) =
    WindowSize.fromWidth(windowWidth).usesNavigationRail && hasRoomForSidePanel(windowWidth - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH)

private val EXPANDED_NAVIGATION_RAIL_MIN_WIDTH = 220.dp

/** The gap the collapsed [NavigationRail] leaves above its first item. */
private val EXPANDED_NAVIGATION_RAIL_TOP_PADDING = 4.dp

/**
 * The navigation bar (under 600dp), navigation rail or expanded navigation rail (see [isNavigationRailExpanded]) that
 * every top level screen shares. It belongs to the bottom of the deck rather than to any one screen: it is laid out
 * once for the window and stays there while the tabs fade through in place next to it. A card dealt over the deck
 * moves the screen under it a little, and the chrome is part of that screen as far as the eye can tell, so for as long
 * as a card covers the deck or is being taken off it every top level screen draws a copy of it instead (see
 * [CampfireScreens]), which moves with the screen and which the card covers.
 */
@Composable
private fun NavigationChrome(
    windowSize: WindowSize,
    isNavigationRailExpanded: Boolean,
    currentTopLevelDestination: CampfireDestination.TopLevel?,
    onDestinationSelected: (CampfireDestination.TopLevel) -> Unit,
) {
    if (isNavigationRailExpanded) {
        // The wide rail's own collapsed state is not used: its collapsed form is wider than the plain rail, and the
        // window size alone decides which of the two a window gets, so there is nothing for the rail to animate
        // between either. The state is only ever the expanded one.
        WideNavigationRail(
            state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
            // Starts under the app bar for the same reason the collapsed rail does, see below.
            windowInsets = WideNavigationRailDefaults.windowInsets.add(WindowInsets(top = TopAppBarDefaults.TopAppBarExpandedHeight)),
            // The default leaves room above the items for a header this rail does not have, which would drop them
            // 40dp lower than the collapsed rail's as the window crosses from the one to the other.
            contentPadding = PaddingValues(top = EXPANDED_NAVIGATION_RAIL_TOP_PADDING),
        ) {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                WideNavigationRailItem(
                    selected = destination == currentTopLevelDestination,
                    onClick = { onDestinationSelected(destination) },
                    icon = { Icon(painter = painterResource(destination.icon), contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                    railExpanded = true,
                )
            }
        }
    } else if (windowSize.usesNavigationRail) {
        NavigationRail(
            // The app bar of every top level screen spans the rail's column, so the rail starts under it. The bar is
            // the pinned, single row one on all three screens, which is what lets its height be known here.
            windowInsets = NavigationRailDefaults.windowInsets.add(WindowInsets(top = TopAppBarDefaults.TopAppBarExpandedHeight)),
        ) {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationRailItem(
                    selected = destination == currentTopLevelDestination,
                    onClick = { onDestinationSelected(destination) },
                    icon = { Icon(painter = painterResource(destination.icon), contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        }
    } else {
        NavigationBar {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationBarItem(
                    selected = destination == currentTopLevelDestination,
                    onClick = { onDestinationSelected(destination) },
                    icon = { Icon(painter = painterResource(destination.icon), contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                )
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
    content: @Composable () -> Unit,
) {
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    Surface(
        modifier = Modifier
            .fillMaxSize()
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
 * One card of the deck next to the navigation chrome, inset from the navigation bar rather than clipped, so that the
 * bar stays visible under it. Given a [chrome], it draws that under itself where [NavigationChromeScaffold] places the
 * shared one, so that nothing moves as the one hands over to the other.
 *
 * It is not inset from the navigation rail, and it is not a surface: the app bar of a top level screen spans the
 * rail's column, so the screen leaves that column open under its bar by itself and paints and blocks only the parts
 * it draws, see [TopLevelScreenLayout]. Not being a surface, it provides the content color one over the background
 * would, or every text and icon on these screens that does not pick a color of its own would be drawn in the
 * default black, which is unreadable in the dark theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopLevelScreenSurface(
    windowSize: WindowSize,
    navigationBarHeight: Dp,
    chrome: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) = Box(
    modifier = Modifier.fillMaxSize(),
) {
    if (chrome != null) {
        Box(
            modifier = Modifier.align(if (windowSize.usesNavigationRail) Alignment.TopStart else Alignment.BottomStart),
        ) {
            chrome()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = navigationBarHeight)
            // The bar covers the insets on its own edge, so nothing inside should apply them a second time.
            .consumeWindowInsets(PaddingValues(bottom = navigationBarHeight)),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
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
 * direction of the other, and the app bar of a top level screen spans the rail, so a screen sliding along the rail
 * would drag its bar across the rail's items.
 *
 * The editor is the one screen that is not a card of the deck but a modal put in front of it, so it always comes up
 * from the bottom edge and leaves the same way, in every direction the gesture that dismisses it may have come
 * from. It is the vertical movement, and not only its Close button, that says the editor is something the song
 * being read is still waiting behind.
 *
 * Whether a transition is a push or a pop is decided here from the depth of the scenes instead of relying on
 * Navigation 3's own detection: when a back stack change interrupts a running transition, Navigation 3 records the
 * already updated back stack as the transition's starting point and animates a pop with the push spec. That leaves
 * the outgoing screen invisible but still covering (and swallowing clicks on) the screen underneath until the
 * animation ends. The same specs are used on every platform (the desktop default would be no animation at all).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.navigationTransition(
    motionScheme: MotionScheme,
): ContentTransform {
    val from = CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey)
    val to = CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey)
    return when {
        from != null && to != null -> tabTransition()
        targetState.zIndex < initialState.zIndex -> popTransition(motionScheme, isModal = isSongEditorTransition)
        else -> pushTransition(motionScheme, isModal = isSongEditorTransition)
    }
}

/**
 * Whether the screen being dealt or taken is the editor, which is the one screen that moves vertically. Either
 * scene can be the one holding it: it is the target of a push and the initial state of a pop.
 */
private val AnimatedContentTransitionScope<Scene<CampfireDestination>>.isSongEditorTransition: Boolean
    get() = CampfireDestination.SongEditor.isContentKey(initialState.entries.lastOrNull()?.contentKey) ||
            CampfireDestination.SongEditor.isContentKey(targetState.entries.lastOrNull()?.contentKey)

/**
 * The card being dealt slides in over the deck. The screen underneath follows in the same direction over a much
 * shorter distance. [ExitTransition.KeepUntilTransitionsFinished] keeps it drawn until the card has landed.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.pushTransition(
    motionScheme: MotionScheme,
    isModal: Boolean,
): ContentTransform {
    val direction = if (isModal) AnimatedContentTransitionScope.SlideDirection.Up else AnimatedContentTransitionScope.SlideDirection.Left
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
    isModal: Boolean,
): ContentTransform {
    val direction = if (isModal) AnimatedContentTransitionScope.SlideDirection.Down else AnimatedContentTransitionScope.SlideDirection.Right
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
 * The pop driven by the predictive back gesture (Android) or the edge swipe (iOS): the same uncovering as
 * [popTransition], except that both screens follow the finger with linear specs. Their direction is fixed by the
 * screen being dismissed, regardless of which edge the gesture started from.
 *
 * Going back from Setlists or Settings to Songs is a swap of tabs rather than a card being taken off, so it cross
 * fades in place, for the reasons [navigationTransition] gives. It is a cross fade rather than [tabTransition]'s fade
 * through, since the gesture can be held anywhere along its way, and a fade through would show neither screen for
 * the middle of it.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.predictivePopTransition(): ContentTransform {
    if (CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey) != null &&
        CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey) != null
    ) {
        val spec = tween<Float>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
        return ContentTransform(
            targetContentEnter = fadeIn(spec),
            initialContentExit = fadeOut(spec),
            targetContentZIndex = targetState.zIndex,
        )
    }
    val towards = if (isSongEditorTransition) AnimatedContentTransitionScope.SlideDirection.Down else AnimatedContentTransitionScope.SlideDirection.Right
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

/**
 * Deeper screens are drawn above shallower ones, so that a pushed screen covers its parent and a popped screen
 * slides away on top of the screen it reveals.
 */
private val Scene<CampfireDestination>.zIndex: Float
    get() = previousEntries.size.toFloat()

private val CampfireDestination.TopLevel.icon: DrawableResource
    get() = when (this) {
        CampfireDestination.Songs -> Res.drawable.ic_songs
        CampfireDestination.Setlists -> Res.drawable.ic_setlists
        CampfireDestination.Settings -> Res.drawable.ic_settings
    }

private val CampfireDestination.TopLevel.label: StringResource
    get() = when (this) {
        CampfireDestination.Songs -> Res.string.songs
        CampfireDestination.Setlists -> Res.string.setlists
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

/** How many of the files an export left out its message names, the rest being counted rather than listed. */
private const val MAXIMUM_NAMED_FILES = 3

/**
 * Tells the platform shell what a running sync should look like while the app is not in front of the user, and that
 * there is nothing to show the moment it ends.
 *
 * Here rather than on the settings screen because a run outlives the screen that started it: the user is free to go
 * back to their songs, or leave the app entirely, and the notification has to follow the run rather than the screen.
 * The strings are resolved here too, so that the notification is in the language chosen inside the app rather than
 * the system's.
 */
@Composable
private fun SyncNotificationEffect(viewModel: CampfireViewModel) {
    val syncNotifier = LocalSyncNotifier.current
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val progress = (syncState as? SyncState.Connected)?.progress
    val channelName = stringResource(Res.string.settings_sync_notification_channel)
    val title = stringResource(Res.string.settings_sync_notification_title)
    val stopLabel = stringResource(Res.string.settings_sync_cancel)
    val preparing = stringResource(Res.string.settings_sync_preparing)
    val progressBodyFormat = stringResource(Res.string.settings_sync_progress)
    val body = if (progress == null || progress.isPreparing) {
        preparing
    } else {
        progressBodyFormat.withSyncCounts(progress.completed, progress.total)
    }
    val notification = progress?.let {
        SyncNotification(
            channelName = channelName,
            title = title,
            body = body,
            preparingBody = preparing,
            progressBodyFormat = progressBodyFormat,
            stopLabel = stopLabel,
            progress = it,
        )
    }
    // "Nothing to show" is only ever said after something was shown from here. Said on the first frame of every
    // composition, it would reach the Android shell as "stop the service" whenever the app was opened onto a run
    // that was already going in the background, and the service takes that as the user's request to stop the run.
    var hasShownNotification by remember { mutableStateOf(false) }
    LaunchedEffect(notification) {
        if (notification != null) {
            hasShownNotification = true
            syncNotifier.onSyncNotificationChanged(notification)
        } else if (hasShownNotification) {
            hasShownNotification = false
            syncNotifier.onSyncNotificationChanged(null)
        }
    }
}
