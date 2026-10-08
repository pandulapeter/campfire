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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.pandulapeter.campfire.presentation.ui.messages.Messages
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.screens.metronome.MetronomeScreen
import com.pandulapeter.campfire.presentation.ui.components.ListLayout
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.dialogs.CampfireDialogs
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportHost
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportTransition
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistsScreen
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReportScreen
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsScreen
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDetailsScreen
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.SongEditorScreen
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongsScreen
import kotlin.math.roundToInt

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
 * The [NavDisplay] and everything laid out over it, sized for the window [NavigationChromeScaffold] measured.
 *
 * While [chromeInScreens] is set, every top level screen draws a navigation chrome of its own, under itself and
 * selected on its own destination, so that the bar or the rail moves with the screen when a card is dealt over it or
 * taken off it, the predictive back gesture included, instead of standing still while the screen slides past it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CampfireScreens(
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

private const val NAVIGATION_GENERATION_METADATA_KEY = "navigationGeneration"
