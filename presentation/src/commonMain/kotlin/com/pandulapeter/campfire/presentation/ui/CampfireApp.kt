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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
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
 */
@Composable
fun CampfireApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
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
    ApplyLanguagePreference(userPreferences?.language)
    CampfireTheme(
        uiMode = userPreferences?.uiMode,
        themeColor = userPreferences?.themeColor,
    ) {
        // Inside the theme, so that the one screen it can put in the way of the app is drawn in the colors the user
        // chose, and above the language preference, so that it is in the language they chose too.
        AppUpdateGate {
            CampfireContent(
                viewModel = viewModel,
                urlOpener = urlOpener,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) = BoxWithConstraints(
    // Painted here as well as on every screen, so that the two screens of a cross fading tab transition blend into
    // the same color they are painted in and the fade stays invisible.
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
) {
    val windowSize = WindowSize.fromWidth(maxWidth)
    val backStack = viewModel.backStack
    val currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    // Makes interrupted transitions retarget instead of getting stuck, see CampfireViewModel.navigationGeneration.
    val navigationMetadata = mapOf(NAVIGATION_GENERATION_METADATA_KEY to viewModel.navigationGeneration)

    // The navigation chrome is laid out once per window size and then never moves: the song details screen is dealt
    // over it and covers it, rather than the two of them animating at the same time. Its thickness is measured
    // instead of assumed, because Material keeps the size of both bars (and the insets they cover) to itself; it is
    // only ever zero on the very first frame, before the first layout pass has run.
    var chromeThickness by remember { mutableStateOf(0.dp) }
    val railWidth = if (windowSize.usesNavigationRail) chromeThickness else 0.dp
    val navigationBarHeight = if (windowSize.usesNavigationRail) 0.dp else chromeThickness

    // The screens next to the chrome always settle at the width the chrome leaves them, whether or not one of them
    // happens to be covered right now; the song details screen covers the chrome, so it settles at the full width.
    // Anything a screen has to decide once, before it is first drawn, is decided from these rather than from the
    // width it is being measured at: the column counts of the song lists and the lyrics, and whether the lists have
    // room for their filter side panel.
    val settledListWidth = maxWidth - railWidth
    val settledSongDetailsWidth = maxWidth

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
        NavigationChrome(
            windowSize = windowSize,
            currentTopLevelDestination = currentTopLevelDestination,
            onDestinationSelected = viewModel::selectTopLevelDestination,
            onThicknessChanged = { chromeThickness = it },
        )
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
    }
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
 * The navigation bar (under 600dp) or navigation rail that every top level screen shares. It belongs to the bottom
 * of the deck rather than to any one screen: it is laid out once for the window and stays there, so it never
 * animates alongside a screen that is sliding, and the song details screen simply covers it.
 *
 * @param onThicknessChanged Reports the width of the rail / the height of the bar, which is how much of the window
 *   is not the top level screens' to use.
 */
@Composable
private fun BoxScope.NavigationChrome(
    windowSize: WindowSize,
    currentTopLevelDestination: CampfireDestination.TopLevel?,
    onDestinationSelected: (CampfireDestination.TopLevel) -> Unit,
    onThicknessChanged: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    if (windowSize.usesNavigationRail) {
        NavigationRail(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onSizeChanged { onThicknessChanged(with(density) { it.width.toDp() }) }
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
        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { onThicknessChanged(with(density) { it.height.toDp() }) }
        ) {
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
        targetState.zIndex < initialState.zIndex -> popTransition(motionScheme)
        else -> pushTransition(motionScheme)
    }
}

/**
 * The card being dealt slides in over the deck. The screen underneath does not animate at all;
 * [ExitTransition.KeepUntilTransitionsFinished] is what keeps it in the composition, unmoved and fully drawn, until
 * the card has landed, instead of it being disposed the moment the back stack changes.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.pushTransition(motionScheme: MotionScheme) = ContentTransform(
    targetContentEnter = slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, motionScheme.defaultSpatialSpec()),
    initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
    targetContentZIndex = targetState.zIndex,
)

/**
 * The top card slides away and the screen underneath is simply uncovered: it is put back on screen at its full size
 * right away ([EnterTransition.None]) and the z indices keep the card that is leaving above it.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.popTransition(motionScheme: MotionScheme) = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, motionScheme.defaultSpatialSpec()),
    targetContentZIndex = targetState.zIndex,
)

/**
 * The pop driven by the predictive back gesture (Android) or the edge swipe (iOS): the same uncovering as
 * [popTransition], except that the card follows the finger, so the spec is linear and the direction depends on the
 * edge the gesture started from.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.predictivePopTransition(@NavigationEvent.SwipeEdge swipeEdge: Int): ContentTransform {
    val towards = if (swipeEdge == NavigationEvent.EDGE_RIGHT) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
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
