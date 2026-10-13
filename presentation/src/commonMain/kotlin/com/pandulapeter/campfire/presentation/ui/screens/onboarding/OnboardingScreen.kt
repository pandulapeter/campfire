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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
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
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.withMinTopEdge
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * The onboarding: a pager of the steps worth asking ([onboardingSteps], worked out from the features as they are now, so
 * the features page's answers take the pages they make pointless off at once), one [OnboardingBottomBar] under all of
 * them, which also holds the way out on the first page. The page shown is
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
        val isLast = nextOnboardingStep(currentStep, steps) == null
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                        isCompact = isCompact,
                    )
                }
            }
            OnboardingBottomBar(
                steps = steps,
                currentStep = currentStep,
                isLast = isLast,
                isOpenedFromSettings = destination.isOpenedFromSettings,
                isImportWaiting = importReport != null,
                onBack = viewModel::navigateBack,
                onClose = viewModel::closeOnboarding,
                onNext = viewModel::showNextOnboardingStep,
            )
        }
    }
}

/** The page of one step, with the settings it reads and the setters it writes through. */
@Composable
private fun OnboardingStepPage(
    viewModel: CampfireViewModel,
    step: OnboardingStep,
    isCompact: Boolean,
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
            language = userPreferences?.language,
            uiMode = userPreferences?.uiMode,
            themeColor = userPreferences?.themeColor,
            backgroundWarmth = userPreferences?.backgroundWarmth ?: 0,
            isCompact = isCompact,
            onLanguageClicked = { viewModel.showDialog(DialogType.AppLanguage) },
            onUiModeSelected = viewModel::setUiMode,
            onThemeColorSelected = viewModel::setThemeColor,
            onBackgroundWarmthChanged = viewModel::setBackgroundWarmth,
        )

        OnboardingStep.FEATURES -> FeaturesStep(
            features = features,
            isCompact = isCompact,
            onChordsEnabledChange = viewModel::setChordsEnabled,
            onSetlistsEnabledChange = viewModel::setSetlistsEnabled,
            onMetronomeEnabledChange = viewModel::setMetronomeEnabled,
            onTunerEnabledChange = viewModel::setTunerEnabled,
        )

        OnboardingStep.INSTRUMENT -> {
            val tunerSettings by viewModel.tunerSettings.collectAsStateWithLifecycle()
            val tuning = InstrumentTuning.fromId(tunerSettings.instrumentId)
            val selected = userPreferences?.let { onboardingInstrumentOf(it.chordInstrument, tuning) }
            // Both settings are written for every answer, since the diagrams depend on the tuning as much as on the
            // instrument: a guitar in drop D has none.
            val setTuning = { instrument: OnboardingInstrument, newTuning: InstrumentTuning? ->
                viewModel.setChordInstrument(instrument.chordInstrumentFor(newTuning))
                viewModel.updateTunerSettings { copy(instrumentId = newTuning?.id ?: InstrumentTuning.CHROMATIC_ID) }
            }
            InstrumentStep(
                selected = selected,
                tuning = tuning,
                notation = (notation ?: UserPreferences.Notation.STANDARD).toChordNotation(),
                areChordsEnabled = features.areChordsEnabled,
                isTunerEnabled = features.isTunerEnabled,
                areChordDiagramsEnabled = userPreferences?.areChordDiagramsEnabled != false,
                isCompact = isCompact,
                onSelected = { instrument -> setTuning(instrument, instrument.tuningFor(tuning)) },
                onTuningSelected = { newTuning -> selected?.let { setTuning(it, newTuning) } },
                onChordDiagramsEnabledChange = viewModel::setChordDiagramsEnabled,
            )
        }

        OnboardingStep.NOTATION -> NotationStep(
            selected = notation,
            isCompact = isCompact,
            onSelected = viewModel::setNotation,
        )

        OnboardingStep.AT_A_GLANCE -> AtAGlanceStep(
            features = features,
            songCardDetails = userPreferences?.songCardDetails ?: UserPreferences.CardDetails.SongsDefault,
            setlistCardDetails = userPreferences?.setlistCardDetails ?: UserPreferences.CardDetails.SetlistsDefault,
            notation = notation,
            isCompact = isCompact,
            onSongCardDetailsChanged = viewModel::setSongCardDetails,
            onSetlistCardDetailsChanged = viewModel::setSetlistCardDetails,
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
    }
}
