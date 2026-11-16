/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.spreadTheWord

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.songDetailsIds
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Asks a regular user, once per device, to tell a musician friend about Campfire (see the package's `CLAUDE.md`): counts
 * every song details screen pushed, and puts [DialogType.SpreadTheWord] up on the way back from one to a list, once
 * that pop's transition has ended.
 *
 * @param backStack The back stack as the app starts with it, restored or not; later changes arrive in [onBackStackChanged].
 * @param today The device's local date, in days since 1970-01-01.
 */
internal class SpreadTheWordController(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    backStack: List<CampfireDestination>,
    private val importController: ImportController,
    private val importReport: StateFlow<ImportReport?>,
    private val syncState: StateFlow<SyncState>,
    private val screenData: StateFlow<DataState<ScreenData>>,
    private val userPreferencesState: StateFlow<DataState<UserPreferences>>,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val today: () -> Long = { Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays() },
) {

    /** The song details screens on the back stack as of its last change, which tells a push from a return to one. */
    private var songDetailsIds = backStack.songDetailsIds()

    /**
     * Whether this process has put the request up. The stored flag alone would do, but it reaches [userPreferencesState]
     * only once the write has gone through the repository and the flows after it, and nothing here may depend on that
     * being quicker than the next return from a song; a write that fails keeps it in memory too, which this repeats.
     */
    private var hasShownRequest = false

    /** Whether the last change of the back stack was a return from song details to a list, see [onTransitionEnded]. */
    private var isReturnFromSongPending = false

    fun onBackStackChanged(previousTop: CampfireDestination?, stack: List<CampfireDestination>) {
        val top = stack.lastOrNull()
        // A push rather than the editor closing over it or a rename rewriting it, which keep its id; an import's
        // preview is not the user's song, and paging between a setlist's songs happens inside one push.
        if (top is CampfireDestination.SongDetails && top.importPreview == null && top.id !in songDetailsIds) countSongOpen()
        songDetailsIds = stack.songDetailsIds()
        isReturnFromSongPending = previousTop is CampfireDestination.SongDetails &&
            previousTop.importPreview == null &&
            (top == CampfireDestination.Songs || top == CampfireDestination.Setlists)
    }

    /**
     * Called once a navigation transition has ended. The request waits for the pop's transition so that the dialog
     * never fights it; any other change of the back stack before then is a reason not to ask this time.
     */
    fun onTransitionEnded() {
        if (!isReturnFromSongPending) return
        isReturnFromSongPending = false
        val preferences = userPreferencesState.value.data ?: return
        val isDue = isSpreadTheWordDue(
            ownSongCount = ownSongCount(screenData.value.data?.unfilteredSongs.orEmpty().map { it.fileName }, DemoLibrary::isDemoSong),
            songOpens = preferences.songOpens,
            isShown = hasShownRequest || preferences.isSpreadTheWordShown,
        )
        val canShow = canShowSpreadTheWord(
            hasDialog = dialogHost.visibleDialog.value != null,
            isImporting = importController.isImporting.value,
            hasImportReport = importReport.value != null,
            queuedImportCount = importController.queuedImportCount.value,
            isOnboardingPending = preferences.isOnboardingPending,
            isWhatsNewOwed = CAMPFIRE_VERSION_NAME !in preferences.seenWhatsNewVersions,
            areSyncDeletionsAwaitingAnswer = (syncState.value as? SyncState.Connected)?.lastOutcome is SyncOutcome.DeletionsNeedConfirmation,
        )
        // Recorded as it appears rather than as it closes: an app ended while it is up does not ask again, since
        // asking twice would cost more than asking once too few.
        if (isDue && canShow && dialogHost.showIfNoneIsShown(DialogType.SpreadTheWord)) {
            hasShownRequest = true
            updatePreferences("remember the request to spread the word") { it.copy(isSpreadTheWordShown = true) }
        }
    }

    private fun countSongOpen() {
        val preferences = userPreferencesState.value.data ?: return
        if (hasShownRequest || preferences.isSpreadTheWordShown || preferences.songOpens.areEnough) return
        val today = today()
        updatePreferences("count the song opened") { it.copy(songOpens = it.songOpens.counted(today)) }
    }

    private fun updatePreferences(description: String, update: (UserPreferences) -> UserPreferences) {
        scope.launch {
            try {
                updateUserPreferences(update)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not $description: ${exception.message}")
            }
        }
    }
}
