/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.launch

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch

/**
 * What the launch screen waits for before it goes, and the place an address asked the app to open on.
 *
 * @param isDemoLibraryPending See `FirstRunController.isDemoLibraryPending`.
 * @param isEditorDraftRecoveryPending Whether a draft a previous run left is still being reopened.
 * @param editorDraftRecovery Completed with whether the editor was reopened on a stored draft.
 * @param restoreNavigationState See `CampfireViewModel.restoreNavigationState`.
 */
internal class LaunchState(
    private val scope: CoroutineScope,
    private val screenData: StateFlow<DataState<ScreenData>>,
    private val isDemoLibraryPending: StateFlow<Boolean>,
    isEditorDraftRecoveryPending: StateFlow<Boolean>,
    private val editorDraftRecovery: Deferred<Boolean>,
    private val userPreferencesState: StateFlow<DataState<UserPreferences>>,
    private val backStack: List<CampfireDestination>,
    private val restoreNavigationState: (NavigationState) -> Boolean,
) {

    /**
     * True while a place the app was asked to open on is waiting for the library to be read, see [navigateOnLaunch].
     * The launch screen waits for it too ([hasLibraryToShow]), so that the app is uncovered on the screen it was
     * asked for rather than on the song list, a moment before that screen slides in over it.
     */
    private val _isLaunchNavigationPending = MutableStateFlow(false)
    val isLaunchNavigationPending: StateFlow<Boolean> = _isLaunchNavigationPending.asStateFlow()

    private var hasNavigatedOnLaunch = false

    /**
     * False for as long as the song list would have nothing on it but a loading indicator, which is what the launch
     * screen stays up in place of. Either the read has put songs together - the first batch of one that publishes as
     * it goes counts, so a slow read still fills the list in front of the user rather than behind the launch screen -
     * or it has finished with none, which is an answer to show as much as a library is. On the one run where a
     * library that has finished empty is about to be filled anyway ([isDemoLibraryPending]), neither is true yet, and
     * neither is it while the screen the app was asked to open on is still being looked for ([isLaunchNavigationPending]),
     * nor while a draft a previous run left is being reopened ([isEditorDraftRecoveryPending]).
     *
     * Latched like `PreferencesController.arePreferencesLoaded`: a rescan reads the library again and says so, and none
     * of that is a reason to put the launch screen back up over an app the user is already using.
     */
    val hasLibraryToShow = combine(
        screenData,
        isDemoLibraryPending,
        _isLaunchNavigationPending,
        isEditorDraftRecoveryPending,
    ) { state, isDemoLibraryPending, isLaunchNavigationPending, isEditorDraftRecoveryPending ->
        !isDemoLibraryPending && !isLaunchNavigationPending && !isEditorDraftRecoveryPending &&
                (state !is DataState.Loading || state.data?.songs?.isNotEmpty() == true)
    }
        .runningFold(false) { hasHadSomethingToShow, hasSomethingToShow -> hasHadSomethingToShow || hasSomethingToShow }
        .asState(scope, false)

    /**
     * Whether the launch screen has already been taken away once. A plain flag rather than a state, since it is only
     * read as the root composition starts: Android recreates its activity, and with it the whole composition, on the
     * configuration changes it does not handle itself (the language and the dark mode, see the manifest) and when the
     * system reclaims it, and a composition that started from nothing would put the launch screen back over an app the
     * user is already using. Worse, it would hold the new activity's first frame back until that screen had faded,
     * which is a frozen window and a few hundred milliseconds of lost taps on every recreation. A process that is
     * started again gets a new view model, which is the start the launch screen is for.
     */
    var hasShownApp = false
        set(value) {
            field = value
            if (value) _isAppOnScreen.value = true
        }

    /**
     * [hasShownApp] as something to wait for, which What's new does, see `FirstRunController.showWhatsNewOnVersionChange`, and as
     * something to watch, which the store's update check does, see `rememberAppUpdateController`.
     */
    private val _isAppOnScreen = MutableStateFlow(false)
    val isAppOnScreen: StateFlow<Boolean> = _isAppOnScreen.asStateFlow()

    /**
     * Opens the app on the place [resolve] picks from the library, once the library has been read - the web build's
     * address, which can name a song or a setlist that only the library can say is still there. The launch screen is
     * held until then, see [isLaunchNavigationPending]. Only the first call counts, since only one start up can be
     * answered, and the answer is only taken while the app is still where every start opens: coming back from a
     * consent page opens Settings or the onboarding on its own, and that is the screen the user is waiting for.
     * [resolve] is told whether performance mode is on as the preferences were read rather than as
     * `PreferencesController.isPerformanceModeEnabled` says, since that state may not have caught up with the read yet,
     * and the mode decides whether an editor may be opened at all.
     *
     * Called by the shell while it is first composed, which is before the read it waits for can possibly have ended:
     * that read resumes on the main thread, and the composition is holding it.
     */
    fun navigateOnLaunch(resolve: (songs: List<Song>, setlists: List<Setlist>, isPerformanceModeEnabled: Boolean) -> NavigationState?) {
        if (hasNavigatedOnLaunch) return
        hasNavigatedOnLaunch = true
        _isLaunchNavigationPending.value = true
        scope.launch {
            try {
                val state = combine(screenData, isDemoLibraryPending) { state, isDemoLibraryPending -> state.takeUnless { isDemoLibraryPending } }
                    .first { it != null && it !is DataState.Loading }
                    ?.data
                val isPerformanceModeEnabled = userPreferencesState.first { it !is DataState.Loading }.data?.isPerformanceModeEnabled == true
                val destination = resolve(state?.unfilteredSongs.orEmpty(), state?.setlists.orEmpty(), isPerformanceModeEnabled)
                // A draft reopened on launch is unsaved text on screen, which an address does not get to replace.
                editorDraftRecovery.await()
                if (destination != null && backStack.toList() == listOf(CampfireDestination.Songs)) {
                    restoreNavigationState(destination)
                }
            } finally {
                _isLaunchNavigationPending.value = false
            }
        }
    }
}
