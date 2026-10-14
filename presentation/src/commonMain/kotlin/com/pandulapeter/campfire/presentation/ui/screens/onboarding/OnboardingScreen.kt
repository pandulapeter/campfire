/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.chrome.contentEdges
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.chrome.withMinTopEdge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * The onboarding: a pager of the steps worth asking ([onboardingSteps], worked out from the features as they are now, so
 * the features page's answers take the page they make pointless off at once), one [OnboardingBottomBar] under all of
 * them, which also holds the way out on the first page. The page shown is
 * [CampfireViewModel.onboardingStep], which the bar's buttons and every way back move - a back steps through the pages
 * before it leaves the screen, see `Navigator.navigateBack` - and the pager follows it. It also takes swipes, a mouse
 * drag, and a trackpad's sideways scroll, and the page they settle on becomes the step, so that the pages turn under the
 * finger as a pager's do, and a predictive back gesture moves them the same way.
 */
@Composable
internal fun OnboardingScreen(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.Onboarding,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    val steps = viewModel.onboardingSteps(userPreferences)
    val currentStep = viewModel.onboardingStep
    val currentSteps by rememberUpdatedState(steps)
    val pagerState = rememberPagerState(initialPage = steps.indexOf(currentStep).coerceAtLeast(0)) { currentSteps.size }
    // Claims the back gesture while there is a page to go back to, rather than leaving it to the back stack's handler,
    // which would preview the screen under the onboarding while the finger is down and then only change the page once it
    // lifts. Registered whether or not it is enabled, since a handler that comes and goes changes the order the
    // dispatcher picks between handlers in.
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = viewModel.isOnboardingBackToPreviousStep,
        onBackCompleted = viewModel::navigateBack,
    )
    val backProgress = remember(backGesture) {
        {
            (backGesture.transitionState as? NavigationEventTransitionState.InProgress)
                ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                ?.latestEvent
                ?.progress
        }
    }
    // The pager follows the step, and a back gesture moves it with the finger towards the page before, inside one scroll
    // that lasts the gesture (as the settings screen's tabs do); letting go animates on from wherever it left the pages,
    // to the page before, which the handler has just made the step, or back where it was for a cancelled gesture.
    val pagerFollowingStep = remember { PagerFollowingStep() }
    LaunchedEffect(pagerState) {
        snapshotFlow { viewModel.onboardingStep to (backProgress() != null) }.collectLatest { (step, isGestureInProgress) ->
            pagerFollowingStep.isMoving = true
            try {
                pagerState.followStep(page = currentSteps.indexOf(step).coerceAtLeast(0), isGestureInProgress = isGestureInProgress, backProgress = backProgress)
            } finally {
                pagerFollowingStep.isMoving = false
            }
        }
    }
    // A page settled on by a swipe becomes the step. Not one settled on while the pager is following the step, which can
    // pass through a page on its way when a second Next or Back takes over from the first.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val step = currentSteps.getOrNull(page)
            if (!pagerFollowingStep.isMoving && step != null && step != viewModel.onboardingStep) viewModel.showOnboardingStep(step)
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.contentEdges.withMinTopEdge),
    ) {
        val isCompact = maxHeight < ONBOARDING_MARK_MIN_HEIGHT
        val sideMargin = onboardingSideMargin(maxWidth)
        val isLast = nextOnboardingStep(currentStep, steps) == null
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Where each page keeps the mark's place, in the window's coordinates, and where the pager itself is in them,
            // since the pages report while they slide and the mark is drawn over all of them.
            val markTops = remember { mutableStateMapOf<OnboardingStep, Float>() }
            var pagerTop by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
                    .onGloballyPositioned { pagerTop = it.positionInRoot().y },
            ) {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                    // The pages next to the one shown are laid out as well, so that the mark knows where it is going
                    // before the first frame of a turn.
                    beyondViewportPageCount = 1,
                    key = { steps.getOrNull(it) ?: it },
                ) { page ->
                    steps.getOrNull(page)?.let { step ->
                        val pageOffset = remember(pagerState, page) { { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction } }
                        CompositionLocalProvider(LocalOnboardingPageOffset provides pageOffset) {
                            OnboardingStepPage(
                                viewModel = viewModel,
                                step = step,
                                markModifier = Modifier.onGloballyPositioned { markTops[step] = it.positionInRoot().y },
                                isCompact = isCompact,
                            )
                        }
                    }
                }
                OnboardingMarkLayer(
                    pagerState = pagerState,
                    steps = steps,
                    markTops = markTops,
                    pagerTop = pagerTop,
                    isCompact = isCompact,
                )
            }
            OnboardingBottomBar(
                steps = steps,
                currentStep = currentStep,
                pagerPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                isLast = isLast,
                isOpenedFromSettings = destination.isOpenedFromSettings,
                isImportWaiting = importReport != null,
                sideMargin = sideMargin,
                onBack = viewModel::navigateBack,
                onClose = viewModel::closeOnboarding,
                onNext = viewModel::showNextOnboardingStep,
            )
        }
    }
}

/**
 * Moves the pager to [page]: with the finger, inside one scroll that lasts a back gesture ([isGestureInProgress],
 * [backProgress]), or animated there otherwise, from wherever a gesture, a swipe, or a turn left it.
 */
private suspend fun PagerState.followStep(page: Int, isGestureInProgress: Boolean, backProgress: () -> Float?) {
    if (isGestureInProgress && page > 0) {
        scroll {
            snapshotFlow { backProgress() }.filterNotNull().collect { progress ->
                val pageWidth = layoutInfo.pageSize + layoutInfo.pageSpacing
                val position = currentPage + currentPageOffsetFraction
                scrollBy((page - progress - position) * pageWidth)
            }
        }
    } else if (currentPage != page || currentPageOffsetFraction != 0f) {
        animateScrollToPage(page)
    }
}

/** Whether the pager is on its way to the step rather than being moved by the user. Not a state: nothing draws it. */
private class PagerFollowingStep(var isMoving: Boolean = false)

/**
 * The mark over the pages, at the pager's position as it is in this frame. A composable of its own, so that a turn
 * recomposes the mark on every frame rather than the screen.
 */
@Composable
private fun BoxScope.OnboardingMarkLayer(
    pagerState: PagerState,
    steps: List<OnboardingStep>,
    markTops: Map<OnboardingStep, Float>,
    pagerTop: Float,
    isCompact: Boolean,
) {
    // Shrinks into the top of its place as the place collapses in a window too short for it, fading as it goes, and
    // grows back out of it the other way; a window that starts short starts without it rather than watching it leave.
    val presence by animateFloatAsState(if (isCompact) 0f else 1f, animationSpec = tween(ONBOARDING_MARK_RESIZE_MILLIS))
    if (presence == 0f) return
    val frame = onboardingMarkFrame(steps, pagerState.currentPage + pagerState.currentPageOffsetFraction) ?: return
    val top = onboardingMarkTop(frame, markTops) ?: return
    OnboardingMark(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(x = 0, y = (top - pagerTop).roundToInt()) }
            .graphicsLayer {
                // Faster than it shrinks, so that the fade reads as one with the shrink rather than as a smaller mark.
                alpha = presence * presence
                scaleX = presence
                scaleY = presence
                transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
            },
        frame = frame,
    )
}

/** The page of one step, with the settings it reads and the setters it writes through. */
@Composable
private fun OnboardingStepPage(
    viewModel: CampfireViewModel,
    step: OnboardingStep,
    markModifier: Modifier,
    isCompact: Boolean,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val features = OnboardingFeatures(
        areChordsEnabled = userPreferences?.areChordsEnabled != false,
        areChordDiagramsEnabled = userPreferences?.areChordDiagramsEnabled != false,
        areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false,
        isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false,
        isTunerEnabled = userPreferences?.isTunerEnabled != false,
    )
    val notation = userPreferences?.chordSpelling?.notation
    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep(
            markModifier = markModifier,
            language = userPreferences?.language,
            isCompact = isCompact,
            onLanguageClicked = { viewModel.showDialog(DialogType.AppLanguage) },
        )

        OnboardingStep.LOOK -> LookStep(
            markModifier = markModifier,
            uiMode = userPreferences?.uiMode,
            themeColor = userPreferences?.themeColor,
            backgroundWarmth = userPreferences?.backgroundWarmth ?: 0,
            isCompact = isCompact,
            onUiModeSelected = viewModel::setUiMode,
            onThemeColorSelected = viewModel::setThemeColor,
            onBackgroundWarmthChanged = viewModel::setBackgroundWarmth,
        )

        OnboardingStep.INSTRUMENT -> InstrumentStep(
            markModifier = markModifier,
            instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR,
            tuning = userPreferences?.instrumentTuning,
            chordTuning = userPreferences?.chordTuning(),
            notation = (notation ?: UserPreferences.Notation.STANDARD).toChordNotation(),
            isCompact = isCompact,
            onInstrumentClicked = { viewModel.showDialog(DialogType.Instrument) },
            onTuningClicked = { viewModel.showDialog(DialogType.Tuning) },
        )

        OnboardingStep.FEATURES -> FeaturesStep(
            markModifier = markModifier,
            features = features,
            chordTuning = userPreferences?.chordTuning(),
            isCompact = isCompact,
            onChordsEnabledChange = viewModel::setChordsEnabled,
            onChordDiagramsEnabledChange = viewModel::setChordDiagramsEnabled,
            onMetronomeEnabledChange = viewModel::setMetronomeEnabled,
            onTunerEnabledChange = viewModel::setTunerEnabled,
            onSetlistsEnabledChange = viewModel::setSetlistsEnabled,
        )

        OnboardingStep.NOTATION -> NotationStep(
            markModifier = markModifier,
            selected = notation,
            accidentals = userPreferences?.chordSpelling?.accidentals,
            isCompact = isCompact,
            onSelected = viewModel::setNotation,
        )

        OnboardingStep.SYNC -> {
            val syncState by viewModel.syncState.collectAsStateWithLifecycle()
            SyncStep(
                markModifier = markModifier,
                syncState = syncState,
                isCompact = isCompact,
                onConnect = { completionPage -> viewModel.connectSyncProvider(SyncProviderId.DROPBOX, completionPage) },
                onCancel = viewModel::cancelSyncConnection,
            )
        }
    }
}
