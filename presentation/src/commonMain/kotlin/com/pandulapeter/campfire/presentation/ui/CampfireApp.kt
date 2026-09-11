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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.error_operation_failed
import com.pandulapeter.campfire.presentation.resources.export_failed
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_result
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
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.areDrawablesLoaded
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.platform.libraryLocation
import com.pandulapeter.campfire.presentation.ui.platform.requestLibraryPersistence
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    // Asked for as early as there is anything to ask from, and never insisted on: on the web this is what stops the
    // browser from evicting the library when the device runs short of space, and everywhere else it is a no-op.
    LaunchedEffect(Unit) { requestLibraryPersistence() }
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
                AppUpdateGate {
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
            var isAppReady by remember { mutableStateOf(false) }
            if (!isAppReady) {
                val opacity = remember { Animatable(1f) }
                // On the desktop the mark grows as it goes, over the slower of the two effect springs so that the
                // movement has the time to be read as one: this is the one platform where the launch screen is the
                // whole of the startup - the first frame the window paints and the last one before the app - so it
                // is worth leaving by opening into the app rather than by merely thinning out. The other three
                // open on a startup screen of their own and never watch this one go: Android's splash and the web's
                // loading page cover the fade entirely, and on iOS the mark is already the second thing shown - so
                // there it stays the plain, quicker dissolve, and the extra frames are not spent.
                val markGrowth = if (isDesktopPlatform) LAUNCH_MARK_EXIT_GROWTH else 0f
                LaunchScreen(
                    modifier = Modifier.graphicsLayer { alpha = opacity.value.coerceIn(0f, 1f) },
                    markScale = { 1f + (1f - opacity.value.coerceIn(0f, 1f)) * markGrowth },
                )
                val motionScheme = MaterialTheme.motionScheme
                val fadeSpec = if (isDesktopPlatform) motionScheme.slowEffectsSpec<Float>() else motionScheme.defaultEffectsSpec<Float>()
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) = NavigationChromeScaffold(
    // Painted here as well as on every screen, so that the two screens of a cross fading tab transition blend into
    // the same color they are painted in and the fade stays invisible.
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    chrome = { windowSize ->
        NavigationChrome(
            windowSize = windowSize,
            currentTopLevelDestination = viewModel.backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel,
            onDestinationSelected = viewModel::selectTopLevelDestination,
        )
    },
) { windowWidth, windowSize, chromeThickness ->
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

    // What is left of the window insets once the chrome has covered the edge it sits on. The screens hand these to
    // their lists as content padding, so that items scroll under the system bars instead of stopping short of them.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val shellContentPadding = PaddingValues(
        start = if (windowSize.usesNavigationRail) 0.dp else systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        bottom = maxOf(
            if (windowSize.usesNavigationRail) systemBars.calculateBottomPadding() else 0.dp,
            // The keyboard covers the navigation bar instead of pushing it away, so only what is left of it counts.
            (imeHeight - navigationBarHeight).coerceAtLeast(0.dp),
        ),
    )
    val songDetailsContentPadding = PaddingValues(
        start = systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        bottom = maxOf(systemBars.calculateBottomPadding(), imeHeight),
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = backStack,
            onBack = viewModel::navigateBack,
            // The same spec decides the direction for both parameters, see navigationTransition.
            transitionSpec = { navigationTransition(motionScheme, windowSize.usesNavigationRail) },
            popTransitionSpec = { navigationTransition(motionScheme, windowSize.usesNavigationRail) },
            predictivePopTransitionSpec = { swipeEdge -> predictivePopTransition(swipeEdge) },
            // Stable string content keys, so that the transitions can recognize the top level destinations.
            entryProvider = entryProvider {
                entry<CampfireDestination.Songs>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                    ReportNavigationTransition(viewModel)
                    ScreenSurface(railWidth, navigationBarHeight) {
                        SongsScreen(
                            viewModel = viewModel,
                            settledWidth = settledListWidth,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Setlists>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                    ReportNavigationTransition(viewModel)
                    ScreenSurface(railWidth, navigationBarHeight) {
                        SetlistsScreen(
                            viewModel = viewModel,
                            settledWidth = settledListWidth,
                            contentPadding = shellContentPadding,
                        )
                    }
                }
                entry<CampfireDestination.Settings>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                    ReportNavigationTransition(viewModel)
                    ScreenSurface(railWidth, navigationBarHeight) {
                        SettingsScreen(
                            viewModel = viewModel,
                            contentPadding = shellContentPadding,
                            urlOpener = urlOpener,
                        )
                    }
                }
                // These two cover the chrome, so they are the only ones laid out edge to edge.
                entry<CampfireDestination.SongEditor>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                    ReportNavigationTransition(viewModel)
                    ScreenSurface {
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
                    ReportNavigationTransition(viewModel)
                    ScreenSurface {
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
        CampfireDialogs(
            viewModel = viewModel,
            urlOpener = urlOpener,
        )
        Messages(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navigationBarHeight + systemBars.calculateBottomPadding()),
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
    // Queued rather than collected straight into the snackbar: the text of a message can only be built in a
    // composition (string resources are composable), and two identical results in a row are still two messages -
    // which is what the numbering is for. Two failed exports are the same object, and an effect keyed on the message
    // alone would not restart for the second one: it would sit at the head of the queue forever, unshown, with
    // everything after it stuck behind it.
    val queue = remember { mutableStateListOf<IndexedValue<CampfireViewModel.Message>>() }
    LaunchedEffect(viewModel) {
        var count = 0
        viewModel.messages.collect { queue += IndexedValue(count++, it) }
    }
    val current: CampfireViewModel.Message? = queue.firstOrNull()?.value
    val text = when (current) {
        is CampfireViewModel.Message.ImportFinished -> stringResource(
            Res.string.import_result,
            current.result.importedSongFileNames.size,
            current.result.importedSetlistFileNames.size,
            current.result.duplicateFileNames.size,
            current.result.skippedFileNames.size,
        )

        CampfireViewModel.Message.ImportFailed -> stringResource(Res.string.import_failed)
        CampfireViewModel.Message.ExportFailed -> stringResource(Res.string.export_failed)
        CampfireViewModel.Message.SaveFailed -> stringResource(Res.string.song_editor_save_failed)
        CampfireViewModel.Message.OperationFailed -> stringResource(Res.string.error_operation_failed)
        null -> null
    }
    LaunchedEffect(queue.firstOrNull()?.index) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            queue.removeFirstOrNull()
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
 * rather than the two of them animating side by side.
 */
@Composable
private fun NavigationChromeScaffold(
    modifier: Modifier = Modifier,
    chrome: @Composable (windowSize: WindowSize) -> Unit,
    content: @Composable (windowWidth: Dp, windowSize: WindowSize, chromeThickness: Dp) -> Unit,
) = SubcomposeLayout(modifier) { constraints ->
    val windowWidth = constraints.maxWidth.toDp()
    val windowSize = WindowSize.fromWidth(windowWidth)
    // Loose constraints, so that the rail and the bar each take only the one dimension they want.
    val chromePlaceable = subcompose(ChromeSlot.CHROME) { chrome(windowSize) }
        .single()
        .measure(constraints.copy(minWidth = 0, minHeight = 0))
    val chromeThickness = if (windowSize.usesNavigationRail) chromePlaceable.width else chromePlaceable.height
    val contentPlaceable = subcompose(ChromeSlot.CONTENT) { content(windowWidth, windowSize, chromeThickness.toDp()) }
        .single()
        .measure(constraints)
    layout(constraints.maxWidth, constraints.maxHeight) {
        // Placed relatively, so that the rail sits on the start edge the screens are inset from rather than always
        // on the left one.
        chromePlaceable.placeRelative(
            x = 0,
            y = if (windowSize.usesNavigationRail) 0 else constraints.maxHeight - chromePlaceable.height,
        )
        contentPlaceable.placeRelative(x = 0, y = 0)
    }
}

private enum class ChromeSlot { CHROME, CONTENT }

/**
 * The navigation bar (under 600dp) or navigation rail that every top level screen shares. It belongs to the bottom
 * of the deck rather than to any one screen: it is laid out once for the window and stays there, so it never
 * animates alongside a screen that is sliding, and the song details screen simply covers it.
 */
@Composable
private fun NavigationChrome(
    windowSize: WindowSize,
    currentTopLevelDestination: CampfireDestination.TopLevel?,
    onDestinationSelected: (CampfireDestination.TopLevel) -> Unit,
) {
    if (windowSize.usesNavigationRail) {
        NavigationRail {
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
 * One card of the deck: an opaque screen, inset by however much of the window the navigation chrome has taken.
 * The screens have to be opaque, otherwise the one being covered would show through the one covering it, and they
 * have to be inset rather than clipped, so that the chrome stays visible next to them.
 *
 * A [Surface] rather than a plain box because it also blocks touches from reaching what is behind it: the chrome is
 * drawn under the screens, so without this the rail would still take taps through the song details screen covering
 * it, and a screen being covered would still take taps through the one landing on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenSurface(
    railWidth: Dp = 0.dp,
    navigationBarHeight: Dp = 0.dp,
    content: @Composable () -> Unit,
) = Surface(
    modifier = Modifier
        .fillMaxSize()
        .padding(start = railWidth, bottom = navigationBarHeight)
        // The chrome covers the insets on its own edge, so nothing inside should apply them a second time.
        .consumeWindowInsets(PaddingValues(start = railWidth, bottom = navigationBarHeight)),
    color = MaterialTheme.colorScheme.background,
) {
    content()
}

/**
 * The screens are a deck of cards. Pushing one deals it over the top of the deck: it slides in from the end while
 * the screen it lands on stays exactly where it is. Popping takes the top card off again: it slides back out and
 * the screen underneath is uncovered, without moving. Nothing ever moves except the card being dealt or taken, so
 * a screen keeps whatever it had on screen (its scroll position, its navigation chrome, the caret in its search
 * field) in the same place across the whole transition.
 *
 * Switching between top level destinations is not a deal but a swap of the bottom card, so those cross fade with a
 * subtle slide in the direction of the tab order: horizontal next to a navigation bar, whose tabs sit next to each
 * other, vertical next to a navigation rail, whose tabs sit above each other.
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
    usesNavigationRail: Boolean,
): ContentTransform {
    val from = CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey)
    val to = CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey)
    return when {
        from != null && to != null -> tabTransition(
            towards = if (to.index > from.index) {
                if (usesNavigationRail) AnimatedContentTransitionScope.SlideDirection.Up else AnimatedContentTransitionScope.SlideDirection.Start
            } else {
                if (usesNavigationRail) AnimatedContentTransitionScope.SlideDirection.Down else AnimatedContentTransitionScope.SlideDirection.End
            }
        )
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
 * The card being dealt slides in over the deck. The screen underneath does not animate at all;
 * [ExitTransition.KeepUntilTransitionsFinished] is what keeps it in the composition, unmoved and fully drawn, until
 * the card has landed, instead of it being disposed the moment the back stack changes.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.pushTransition(
    motionScheme: MotionScheme,
    isModal: Boolean,
) = ContentTransform(
    targetContentEnter = slideIntoContainer(
        towards = if (isModal) AnimatedContentTransitionScope.SlideDirection.Up else AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = motionScheme.defaultSpatialSpec(),
    ),
    initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
    targetContentZIndex = targetState.zIndex,
)

/**
 * The top card slides away and the screen underneath is simply uncovered: it is put back on screen at its full size
 * right away ([EnterTransition.None]) and the z indices keep the card that is leaving above it.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.popTransition(
    motionScheme: MotionScheme,
    isModal: Boolean,
) = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = slideOutOfContainer(
        towards = if (isModal) AnimatedContentTransitionScope.SlideDirection.Down else AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = motionScheme.defaultSpatialSpec(),
    ),
    targetContentZIndex = targetState.zIndex,
)

/**
 * The pop driven by the predictive back gesture (Android) or the edge swipe (iOS): the same uncovering as
 * [popTransition], except that the card follows the finger, so the spec is linear and the direction depends on the
 * edge the gesture started from. The modal goes down whichever edge the finger came from, since that is the one
 * way it ever leaves.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.predictivePopTransition(@NavigationEvent.SwipeEdge swipeEdge: Int): ContentTransform {
    val towards = when {
        isSongEditorTransition -> AnimatedContentTransitionScope.SlideDirection.Down
        swipeEdge == NavigationEvent.EDGE_RIGHT -> AnimatedContentTransitionScope.SlideDirection.Left
        else -> AnimatedContentTransitionScope.SlideDirection.Right
    }
    return ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = slideOutOfContainer(towards, tween(PREDICTIVE_BACK_DURATION, easing = LinearEasing)),
        targetContentZIndex = targetState.zIndex,
    )
}

/**
 * The slide is a fraction of the size the screens travel along (their width for a horizontal direction, their height
 * for a vertical one), so the same fraction works in both directions.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.tabTransition(towards: AnimatedContentTransitionScope.SlideDirection) = ContentTransform(
    targetContentEnter = fadeIn(tween(TAB_TRANSITION_DURATION)) + slideIntoContainer(towards, tween(TAB_TRANSITION_DURATION)) { it / TAB_SLIDE_FRACTION },
    initialContentExit = fadeOut(tween(TAB_TRANSITION_DURATION)) + slideOutOfContainer(towards, tween(TAB_TRANSITION_DURATION)) { it / TAB_SLIDE_FRACTION },
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
 * a back stack change interrupts an animation (see [CampfireViewModel.navigationGeneration]).
 */
@Composable
private fun ReportNavigationTransition(viewModel: CampfireViewModel) {
    val isRunning = LocalNavAnimatedContentScope.current.transition.isRunning
    SideEffect { viewModel.setNavigationTransitionRunning(isRunning) }
}

private val LAUNCH_MARK_SIZE = 72.dp

/**
 * How much the mark has grown by the time it has faded away, on the platform that watches it leave: half as large
 * again, which is enough of a movement to be seen for what it is over the length of the fade rather than read as
 * the picture drifting.
 */
private const val LAUNCH_MARK_EXIT_GROWTH = 0.5f
private const val NAVIGATION_GENERATION_METADATA_KEY = "navigationGeneration"
private const val TAB_TRANSITION_DURATION = 300
private const val TAB_SLIDE_FRACTION = 12
private const val PREDICTIVE_BACK_DURATION = 350

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
