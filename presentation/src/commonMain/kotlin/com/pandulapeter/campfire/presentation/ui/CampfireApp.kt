package com.pandulapeter.campfire.presentation.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.dialogs.CampfireDialogs
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistsScreen
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsScreen
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDetailsScreen
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongsScreen
import com.pandulapeter.campfire.presentation.ui.theme.ApplyLanguagePreference
import com.pandulapeter.campfire.presentation.ui.theme.CampfireTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The root of the shared Compose UI: theme, adaptive navigation chrome, the Navigation 3 display and the dialogs.
 *
 * @param urlOpener Opens the given URL in the platform's browser.
 */
@Composable
fun CampfireApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    ApplyLanguagePreference(userPreferences?.language)
    CampfireTheme(
        uiMode = userPreferences?.uiMode
    ) {
        CampfireContent(
            viewModel = viewModel,
            urlOpener = urlOpener
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CampfireContent(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit
) = BoxWithConstraints(
    modifier = Modifier.fillMaxSize()
) {
    val windowSize = WindowSize.fromWidth(maxWidth)
    // Both bars are hidden on the song details screen, so it always settles at the full width of the window.
    val settledSongDetailsWidth = maxWidth
    val backStack = viewModel.backStack
    val currentDestination = backStack.lastOrNull()
    val currentTopLevelDestination = backStack.lastOrNull { it is CampfireDestination.TopLevel } as? CampfireDestination.TopLevel
    val isSongDetailsOpen = currentDestination is CampfireDestination.SongDetails
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val isNavigationBarVisible = !windowSize.usesNavigationRail && !isSongDetailsOpen && !isImeVisible
    val isNavigationRailVisible = windowSize.usesNavigationRail && !isSongDetailsOpen
    val layoutDirection = LocalLayoutDirection.current
    val motionScheme = MaterialTheme.motionScheme
    // Makes interrupted transitions retarget instead of getting stuck, see CampfireViewModel.navigationGeneration.
    val navigationMetadata = mapOf(NAVIGATION_GENERATION_METADATA_KEY to viewModel.navigationGeneration)

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        bottomBar = {
            // Expanding / shrinking (instead of sliding) lets the content area follow the bar frame by frame.
            AnimatedVisibility(
                visible = isNavigationBarVisible,
                enter = expandVertically(BAR_SIZE_SPEC) + fadeIn(motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(BAR_SIZE_SPEC) + fadeOut(motionScheme.defaultEffectsSpec())
            ) {
                NavigationBar {
                    CampfireDestination.TopLevel.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentTopLevelDestination,
                            onClick = { viewModel.selectTopLevelDestination(destination) },
                            icon = { Icon(painter = painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Scale only: a fade would wrap the button in an alpha layer, which composites at its layout bounds and
            // cuts the shadow off. Scaling from zero hides it just as well.
            AnimatedVisibility(
                visible = currentTopLevelDestination == CampfireDestination.Setlists && !isSongDetailsOpen,
                enter = scaleIn(motionScheme.defaultSpatialSpec()),
                exit = scaleOut(motionScheme.defaultSpatialSpec())
            ) {
                FloatingActionButton(
                    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.NewSetlist) }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = stringResource(Res.string.setlists_new_setlist)
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // The rail shrinks with almost the same spatial spring that moves the screens, so the content follows it
            // smoothly, see BAR_SIZE_SPEC.
            AnimatedVisibility(
                visible = isNavigationRailVisible,
                enter = expandHorizontally(BAR_SIZE_SPEC) + fadeIn(motionScheme.defaultEffectsSpec()),
                exit = shrinkHorizontally(BAR_SIZE_SPEC) + fadeOut(motionScheme.defaultEffectsSpec())
            ) {
                NavigationRail {
                    CampfireDestination.TopLevel.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == currentTopLevelDestination,
                            onClick = { viewModel.selectTopLevelDestination(destination) },
                            icon = { Icon(painter = painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) }
                        )
                    }
                }
            }
            // The rail already covers the start inset, the screens only need to handle the bottom and the end.
            val contentPadding = PaddingValues(
                start = if (isNavigationRailVisible) 0.dp else innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding()
            )
            NavDisplay(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .consumeWindowInsets(if (isNavigationRailVisible) WindowInsets.systemBars.only(WindowInsetsSides.Start) else WindowInsets(0)),
                backStack = backStack,
                onBack = viewModel::navigateBack,
                // The same spec decides the direction for both parameters, see navigationTransition.
                transitionSpec = { navigationTransition(motionScheme) },
                popTransitionSpec = { navigationTransition(motionScheme) },
                predictivePopTransitionSpec = { swipeEdge -> predictivePopTransition(swipeEdge) },
                // Stable string content keys, so that the transitions can recognize the top level destinations.
                entryProvider = entryProvider {
                    entry<CampfireDestination.Songs>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                        ReportNavigationTransition(viewModel)
                        SongsScreen(
                            viewModel = viewModel,
                            contentPadding = contentPadding
                        )
                    }
                    entry<CampfireDestination.Setlists>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                        ReportNavigationTransition(viewModel)
                        SetlistsScreen(
                            viewModel = viewModel,
                            contentPadding = contentPadding
                        )
                    }
                    entry<CampfireDestination.Settings>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) {
                        ReportNavigationTransition(viewModel)
                        SettingsScreen(
                            viewModel = viewModel,
                            contentPadding = contentPadding,
                            urlOpener = urlOpener
                        )
                    }
                    entry<CampfireDestination.SongDetails>(metadata = navigationMetadata, clazzContentKey = { it.contentKey }) { destination ->
                        ReportNavigationTransition(viewModel)
                        SongDetailsScreen(
                            viewModel = viewModel,
                            destination = destination,
                            windowSize = windowSize,
                            settledWidth = settledSongDetailsWidth,
                            contentPadding = contentPadding,
                            onBack = viewModel::navigateBack
                        )
                    }
                }
            )
        }
    }
    CampfireDialogs(
        viewModel = viewModel,
        urlOpener = urlOpener
    )
}

/**
 * Switching between top level destinations uses a fade through with a subtle slide in the direction of the tab
 * order. Pushing a screen slides it in over the current one, which drifts away with a parallax effect; popping
 * reverses that.
 *
 * Whether a transition is a push or a pop is decided here from the depth of the scenes instead of relying on
 * Navigation 3's own detection: when a back stack change interrupts a running transition, Navigation 3 records the
 * already updated back stack as the transition's starting point and animates a pop with the push spec. That leaves
 * the outgoing screen invisible but still covering (and swallowing clicks on) the screen underneath until the
 * animation ends. The same specs are used on every platform (the desktop default would be no animation at all).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.navigationTransition(motionScheme: MotionScheme): ContentTransform {
    val from = CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey)
    val to = CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey)
    return when {
        from != null && to != null ->
            tabTransition(towards = if (to.index > from.index) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End)
        targetState.zIndex < initialState.zIndex -> popTransition(motionScheme)
        else -> pushTransition(motionScheme)
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.pushTransition(motionScheme: MotionScheme) = ContentTransform(
    targetContentEnter = slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, motionScheme.defaultSpatialSpec()) +
            fadeIn(motionScheme.defaultEffectsSpec()),
    initialContentExit = slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, motionScheme.defaultSpatialSpec()) { it / PARALLAX_FRACTION } +
            fadeOut(motionScheme.defaultEffectsSpec()),
    targetContentZIndex = targetState.zIndex
)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.popTransition(motionScheme: MotionScheme) = ContentTransform(
    targetContentEnter = slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, motionScheme.defaultSpatialSpec()) { it / PARALLAX_FRACTION } +
            fadeIn(motionScheme.defaultEffectsSpec()),
    initialContentExit = slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, motionScheme.defaultSpatialSpec()) +
            fadeOut(motionScheme.defaultEffectsSpec()),
    targetContentZIndex = targetState.zIndex
)

/**
 * The pop transition driven by the predictive back gesture (Android) or the edge swipe (iOS). The progress follows
 * the finger, so the specs are linear and the slide direction depends on the edge the gesture started from.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.predictivePopTransition(@NavigationEvent.SwipeEdge swipeEdge: Int): ContentTransform {
    val towards = if (swipeEdge == NavigationEvent.EDGE_RIGHT) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
    val spec = tween<IntOffset>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
    return ContentTransform(
        targetContentEnter = slideIntoContainer(towards, spec) { it / PARALLAX_FRACTION } + fadeIn(tween(PREDICTIVE_BACK_DURATION, easing = LinearEasing)),
        initialContentExit = slideOutOfContainer(towards, spec) + fadeOut(tween(PREDICTIVE_BACK_DURATION, easing = LinearEasing)),
        targetContentZIndex = targetState.zIndex
    )
}

@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.tabTransition(towards: AnimatedContentTransitionScope.SlideDirection) = ContentTransform(
    targetContentEnter = fadeIn(tween(TAB_TRANSITION_DURATION)) + slideIntoContainer(towards, tween(TAB_TRANSITION_DURATION)) { it / TAB_SLIDE_FRACTION },
    initialContentExit = fadeOut(tween(TAB_TRANSITION_DURATION)) + slideOutOfContainer(towards, tween(TAB_TRANSITION_DURATION)) { it / TAB_SLIDE_FRACTION },
    targetContentZIndex = targetState.zIndex
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

/**
 * Resizes the navigation bars, which is what gives the screens next to them their width.
 *
 * Deliberately not the motion scheme's spatial spec, which the screens themselves move with, for two reasons.
 *
 * Its springs are slightly underdamped, and the bars would overshoot their final size by a pixel or two at the end
 * of the animation. That is invisible on the bar itself, but the screen next to it is that much narrower for a few
 * frames, which is enough to drop a column from the song list when the window happens to be exactly as wide as a
 * whole number of columns (the default desktop window is: 800dp minus the 80dp rail is exactly two columns).
 *
 * And it settles at about the same time as the screen it is animating next to. Everything the screens lay out from
 * their width reflows for as long as the bar is still moving, down to individual lines of lyrics rewrapping, so the
 * last of those reflows would land on the frame the transition ends on, in front of a screen that has come to rest.
 * This is stiff enough to settle in the first third of the transition instead, while the screen arriving over it is
 * still moving and half transparent.
 */
private val BAR_SIZE_SPEC = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = IntSize.VisibilityThreshold
)

private const val NAVIGATION_GENERATION_METADATA_KEY = "navigationGeneration"
private const val TAB_TRANSITION_DURATION = 300
private const val TAB_SLIDE_FRACTION = 12
private const val PARALLAX_FRACTION = 4
private const val PREDICTIVE_BACK_DURATION = 350
