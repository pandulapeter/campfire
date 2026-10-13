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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.onboarding_skip
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.withMinTopEdge
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.painterResource

/**
 * The onboarding: the way out at the top - Skip on a first run, Close when it was opened from Settings - a pager of the
 * steps worth asking ([onboardingSteps], worked out from the features as they are now, so the role page's answers take
 * the pages they make pointless off at once), and one [OnboardingBottomBar] under all of them. The page shown is
 * [CampfireViewModel.onboardingStep], which the bar's buttons and every way back move - a back steps through the pages
 * before it leaves the screen, see `Navigator.navigateBack` - and the pager follows it. It takes no swipes of its own,
 * since a page is finished with its button rather than flicked past.
 */
@Composable
internal fun OnboardingScreen(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.Onboarding,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val steps = viewModel.onboardingSteps(userPreferences)
    val currentStep = viewModel.onboardingStep
    val currentSteps by rememberUpdatedState(steps)
    val pagerState = rememberPagerState(initialPage = steps.indexOf(currentStep).coerceAtLeast(0)) { currentSteps.size }
    // The role picked since the screen was opened, which is not stored: the switches under it are what the app goes by.
    var role by rememberSaveable { mutableStateOf<OnboardingRole?>(null) }
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
    LaunchedEffect(pagerState) {
        snapshotFlow { viewModel.onboardingStep to (backProgress() != null) }.collectLatest { (step, isGestureInProgress) ->
            val page = currentSteps.indexOf(step).coerceAtLeast(0)
            if (isGestureInProgress && page > 0) {
                pagerState.scroll {
                    snapshotFlow { backProgress() }.filterNotNull().collect { progress ->
                        val pageWidth = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        scrollBy((page - progress - position) * pageWidth)
                    }
                }
            } else if (pagerState.currentPage != page || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.animateScrollToPage(page)
            }
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.contentEdges.withMinTopEdge),
    ) {
        val isCompact = maxHeight < ONBOARDING_MARK_MIN_HEIGHT
        val isShort = maxHeight < SHORT_WINDOW_HEIGHT
        val isLast = nextOnboardingStep(currentStep, steps) == null
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingTopBar(
                modifier = Modifier.height(if (isShort) 48.dp else 56.dp),
                isOpenedFromSettings = destination.isOpenedFromSettings,
                isSkipVisible = !isLast,
                onClose = viewModel::closeOnboarding,
            )
            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState,
                userScrollEnabled = false,
                key = { steps.getOrNull(it) ?: it },
            ) { page ->
                steps.getOrNull(page)?.let { step ->
                    OnboardingStepPage(
                        viewModel = viewModel,
                        step = step,
                        role = role,
                        isCompact = isCompact,
                        onRoleSelected = { role = it },
                    )
                }
            }
            OnboardingBottomBar(
                currentIndex = steps.count { it < currentStep },
                count = steps.size,
                isLast = isLast,
                isOpenedFromSettings = destination.isOpenedFromSettings,
                onBack = viewModel::navigateBack,
                onNext = viewModel::showNextOnboardingStep,
            )
        }
    }
}

/**
 * The way out of the whole onboarding, as every onboarding keeps it: a first run's Skip at the top end, gone on the last
 * page, whose own button already finishes it, and Close at the top start of one opened from Settings, which is a screen
 * the user went to rather than one put in front of them, and is left the way the app's other full screens are.
 */
@Composable
private fun OnboardingTopBar(
    modifier: Modifier = Modifier,
    isOpenedFromSettings: Boolean,
    isSkipVisible: Boolean,
    onClose: () -> Unit,
) = Box(
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    contentAlignment = if (isOpenedFromSettings) Alignment.CenterStart else Alignment.CenterEnd,
) {
    if (isOpenedFromSettings) {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(Res.drawable.ic_clear),
                contentDescription = stringResource(Res.string.close),
            )
        }
    } else {
        AnimatedVisibility(
            visible = isSkipVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TextButton(onClick = onClose) { Text(stringResource(Res.string.onboarding_skip)) }
        }
    }
}

/** The page of one step, with the settings it reads and the setters it writes through. */
@Composable
private fun OnboardingStepPage(
    viewModel: CampfireViewModel,
    step: OnboardingStep,
    role: OnboardingRole?,
    isCompact: Boolean,
    onRoleSelected: (OnboardingRole) -> Unit,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val features = OnboardingFeatures(
        areChordsEnabled = userPreferences?.areChordsEnabled != false,
        areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false,
        isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false,
        isTunerEnabled = userPreferences?.isTunerEnabled != false,
    )
    val notation = userPreferences?.chordSpelling?.notation
    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep(
            uiMode = userPreferences?.uiMode,
            themeColor = userPreferences?.themeColor,
            notation = notation,
            isCompact = isCompact,
            onUiModeSelected = viewModel::setUiMode,
            onThemeColorSelected = viewModel::setThemeColor,
        )

        OnboardingStep.ROLE -> RoleStep(
            role = role,
            features = features,
            isCompact = isCompact,
            onRoleSelected = { picked ->
                onRoleSelected(picked)
                viewModel.applyOnboardingFeatures(picked.features)
            },
            onChordsEnabledChange = viewModel::setChordsEnabled,
            onSetlistsEnabledChange = viewModel::setSetlistsEnabled,
            onMetronomeEnabledChange = viewModel::setMetronomeEnabled,
            onTunerEnabledChange = viewModel::setTunerEnabled,
        )

        OnboardingStep.INSTRUMENT -> {
            val tunerSettings by viewModel.tunerSettings.collectAsStateWithLifecycle()
            val tuning = InstrumentTuning.fromId(tunerSettings.instrumentId)
            InstrumentStep(
                selected = userPreferences?.let { onboardingInstrumentOf(it.chordInstrument, tuning) },
                areChordsEnabled = features.areChordsEnabled,
                isTunerEnabled = features.isTunerEnabled,
                areChordDiagramsEnabled = userPreferences?.areChordDiagramsEnabled != false,
                isCompact = isCompact,
                onSelected = { instrument ->
                    viewModel.setChordInstrument(instrument.chordInstrument)
                    val newTuning = instrument.tuningFor(tuning)
                    viewModel.updateTunerSettings { copy(instrumentId = newTuning?.id ?: InstrumentTuning.CHROMATIC_ID) }
                },
                onChordDiagramsEnabledChange = viewModel::setChordDiagramsEnabled,
            )
        }

        OnboardingStep.NOTATION -> NotationStep(
            selected = notation,
            isCompact = isCompact,
            onSelected = viewModel::setNotation,
        )

        OnboardingStep.AT_A_GLANCE -> AtAGlanceStep(
            areChordsEnabled = features.areChordsEnabled,
            isMetronomeEnabled = features.isMetronomeEnabled,
            isKeyShown = userPreferences?.isKeyShown != false,
            isTempoShown = userPreferences?.isTempoShown != false,
            notation = notation,
            isCompact = isCompact,
            onKeyShownChange = viewModel::setKeyShown,
            onTempoShownChange = viewModel::setTempoShown,
        )

        OnboardingStep.SYNC -> {
            val syncState by viewModel.syncState.collectAsStateWithLifecycle()
            SyncStep(
                syncState = syncState,
                isCompact = isCompact,
                onConnect = { completionPage -> viewModel.connectSyncProvider(SyncProviderId.DROPBOX, completionPage) },
                onCancel = viewModel::cancelSyncConnection,
            )
        }

        OnboardingStep.DONE -> {
            val syncState by viewModel.syncState.collectAsStateWithLifecycle()
            DoneStep(
                features = features,
                isSyncConnected = syncState is SyncState.Connected,
                isCompact = isCompact,
            )
        }
    }
}
