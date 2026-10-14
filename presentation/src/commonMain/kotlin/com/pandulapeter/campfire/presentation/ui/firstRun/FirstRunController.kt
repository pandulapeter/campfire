/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.whats_new_message
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.settings.DemoLibraryOffer
import com.pandulapeter.campfire.presentation.ui.state.ImportController
import com.pandulapeter.campfire.presentation.ui.state.ImportRequest
import com.pandulapeter.campfire.presentation.ui.state.ImportSource
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's first run and what each start owes the user: the demo library planted on a first run, the onboarding and
 * What's new.
 *
 * @param isFirstLaunch Whether this is the first launch of this installation, see `CampfireViewModel.isFirstLaunch`.
 * @param openOnboarding Puts the first page of the onboarding on the back stack.
 * @param awaitAppOnScreen Returns once the app is on screen, see `LaunchState.isAppOnScreen`.
 */
internal class FirstRunController(
    private val scope: CoroutineScope,
    private val isFirstLaunch: Deferred<Boolean>,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val importController: ImportController,
    private val importReport: StateFlow<ImportReport?>,
    private val backStack: List<CampfireDestination>,
    private val screenData: StateFlow<DataState<ScreenData>>,
    private val userPreferencesState: StateFlow<DataState<UserPreferences>>,
    private val songFilter: StateFlow<SongFilter>,
    private val getScreenData: GetScreenDataUseCase,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val openOnboarding: () -> Unit,
    private val awaitAppOnScreen: suspend () -> Unit,
) {

    /**
     * True until the app has settled whether it is planting the demo library, and until it has finished if it is,
     * see [plantDemoLibraryOnFirstRun]. It starts out true rather than being set when the planting begins, because
     * that decision takes a read of its own and the scan of the library can finish first: an empty library would
     * then be announced as the answer, a moment before the songs on their way into it arrived.
     */
    private val _isDemoLibraryPending = MutableStateFlow(true)
    val isDemoLibraryPending: StateFlow<Boolean> = _isDemoLibraryPending.asStateFlow()

    /**
     * Completed once the first run has planted the demo library or found no reason to, which the consumer of
     * `ImportController`'s queue waits for before it takes its first batch: a file opened with the app as it starts for
     * the first time is queued long before that decision, and it belongs in a library that already has the demo in it
     * rather than the other way round - where the file has a demo song's name, the question about it is then asked of the
     * file the user opened, and not of songs they never asked for.
     */
    val demoLibraryDecision = CompletableDeferred<Unit>()

    /** True from the moment the demo library is asked for until the offer has caught up with the outcome, see [importDemoLibrary]. */
    private val isAddingDemoLibrary = MutableStateFlow(false)

    /**
     * The settings screen's offer to add the demo library, null for as long as there is nothing to offer: while the
     * library holds all of it, and until the library has been read, like `LibraryState.librarySummary` and for the same reason -
     * the offer must not appear for a moment over a library that turns out to hold it.
     *
     * Whether it is there is answered by what is on disk rather than by anything the app remembers doing, so deleting
     * one of the demo songs brings the offer back - which is what makes it a way to restore them as well as a way to
     * get them.
     *
     * The offer and whether it can be taken are one value on purpose. As two, the import finishing and the library it
     * wrote reach the screen as separate updates, and the row would be enabled again for as long as the second one
     * takes, which is exactly while it is fading out of the list.
     */
    val demoLibraryOffer = combine(screenData, isAddingDemoLibrary, importController.isImporting) { state, isAddingDemoLibrary, isImporting ->
        when (state.data?.takeIf { it.isWholeLibrary }?.let { DemoLibrary.isPresentIn(songs = it.unfilteredSongs, setlists = it.setlists) }) {
            false -> if (isAddingDemoLibrary || isImporting) DemoLibraryOffer.UNAVAILABLE else DemoLibraryOffer.AVAILABLE
            true, null -> null
        }
    }.asState(scope, null)

    /**
     * The songs the app is shipped with, put into the library the way any other batch of files is. The settings
     * screen offers this for as long as they are not all there, so the same action both plants them and puts back
     * the ones that have been deleted.
     */
    fun importDemoLibrary() = scope.launch {
        if (importController.isImporting.value || !isAddingDemoLibrary.compareAndSet(expect = false, update = true)) return@launch
        val files = readDemoLibrary()
        if (files == null) {
            messageSink.sendMessage(Message.ImportFailed)
        } else {
            importController.enqueueImport(ImportSource.Files(files), shouldReview = false, isDemoLibrary = true).await()
        }
        // Asked of a fresh read of the library rather than of the offer, which can still be a step behind the import
        // that just finished. Where the demo is now all there, the offer is waited for until it has left the screen,
        // so the row fades out disabled instead of coming back for the moments in between.
        val library = getScreenData(songFilter).first { it !is DataState.Loading }.data
        if (library != null && DemoLibrary.isPresentIn(songs = library.unfilteredSongs, setlists = library.setlists)) {
            demoLibraryOffer.first { it == null }
        }
        isAddingDemoLibrary.update { false }
    }

    /**
     * The one thing the app ever puts into the library without being asked, and it only happens to an installation
     * that has nothing of its own: a first run whose scan of the library came back empty. Somebody who has been
     * using Campfire and simply never changed a setting reads as a first run too, which is why the library has to
     * be empty as well - and if it is, then they are a new user by every measure that matters here.
     *
     * Nothing is reported, not even a failure: none of this was asked for, and an empty library is what the user
     * was going to be shown anyway. The preferences are written at the end instead, on every first run whether
     * anything was planted or not, which is what makes the next launch not a first run: without it, deleting every
     * demo song would be undone by the next launch, and so would a library the user filled on the first day and
     * emptied later without ever changing a setting.
     *
     * The same write marks the onboarding as owed ([UserPreferences.isOnboardingPending]), and every later start that
     * finds it still owed opens it again, since an app ended in the middle of the onboarding has not been set up.
     */
    suspend fun plantDemoLibraryOnFirstRun() {
        try {
            if (isFirstLaunch.await()) {
                // Waited for rather than raced: what is being asked is whether the library is empty, and every
                // library looks empty while it is still being read. Nothing else writes to it meanwhile, since the
                // import queue waits for this.
                val library = screenData.first { it !is DataState.Loading }.data
                // An unreadable songs or setlists folder stands in empty so that the rest can be shown, and is not an
                // empty library to plant into.
                if (library != null && library.isWholeLibrary && library.unfilteredSongs.isEmpty() && library.setlists.isEmpty()) {
                    // Imported here rather than through importQueue, where a file opened with the app is already
                    // waiting; see demoLibraryDecision. An empty library has no names for it to collide with, so
                    // this never asks anything.
                    readDemoLibrary()?.let { files ->
                        importController.import(
                            ImportRequest(
                                source = ImportSource.Files(files),
                                shouldAnnounceResult = false,
                                shouldOpenSong = false,
                                shouldReview = false,
                                isDemoLibrary = true,
                            ),
                        )
                        importController.awaitImportSettled()
                    }
                }
                openOnboardingOnStartUp()
                // Before the preferences are written rather than after: neither the queue nor the launch screen has any
                // reason to wait for those.
                demoLibraryDecision.complete(Unit)
                _isDemoLibraryPending.update { false }
                // Read and written by the repository rather than copied from userPreferencesState, which can lag it by a
                // dispatch and would write the demo record the import just took back out. With nothing read there is
                // nothing to write either, and the next start is a first run again - which plants nothing into a library
                // that has songs in it.
                updateUserPreferences { it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME, isOnboardingPending = true) }
            } else if (userPreferencesState.first { it !is DataState.Loading }.data?.isOnboardingPending == true) {
                // Only onto a stack nothing has been built on yet: a process restored behind the onboarding already has it,
                // and one restored on another screen has a user somewhere the onboarding must not cover.
                if (backStack.toList() == listOf(CampfireDestination.Songs)) openOnboardingOnStartUp()
            }
        } finally {
            // In a finally rather than at the end: whatever went wrong, the app is no longer waiting for this, and
            // the launch screen is over the whole of it - and neither is the queue of imports.
            demoLibraryDecision.complete(Unit)
            _isDemoLibraryPending.update { false }
        }
    }

    /**
     * Puts the onboarding on the back stack of a start up that owes it, while the launch screen is still up, so that the app is
     * uncovered on its first page rather than on the songs with the page sliding in over them a moment later. A file the
     * app was opened with does not hold it back: it is already queued by now (the queue waits for [demoLibraryDecision],
     * which is completed after this), its import is read behind the onboarding, and its import screen ([ImportReport])
     * is pushed as the onboarding leaves, whose last button says Next meanwhile. Only a dialog holds it back.
     * It is part of the saved back stack like any other screen, so a process the system ends behind it comes back on it,
     * every answer given so far kept, since each is written as it is given.
     */
    private fun openOnboardingOnStartUp() {
        if (canShowOnboarding(hasDialog = dialogHost.visibleDialog.value != null)) openOnboarding()
    }

    /**
     * Clears [UserPreferences.isOnboardingPending] once the onboarding has left the back stack - finished, skipped, or
     * gone back out of from its first page, every one of them a way the user chose out of it. A process that ends with
     * it on screen never gets here, so the next start opens it again.
     */
    fun onBackStackChanged(stack: List<CampfireDestination>) {
        val isOnboardingOpen = stack.any { it is CampfireDestination.Onboarding }
        if (wasOnboardingOpen && !isOnboardingOpen) {
            scope.launch {
                try {
                    withContext(NonCancellable) { updateUserPreferences { it.copy(isOnboardingPending = false) } }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not remember that the onboarding is done: ${exception.message}")
                }
            }
        }
        wasOnboardingOpen = isOnboardingOpen
    }

    /** Whether the back stack held the onboarding as of its last change, see [onBackStackChanged]. */
    private var wasOnboardingOpen = backStack.any { it is CampfireDestination.Onboarding }

    /**
     * The first installed version belongs to the onboarding, so it is recorded by [plantDemoLibraryOnFirstRun] instead.
     * Later versions wait for the app and for every import queued, running or reported on before opening (see
     * [canShowWhatsNew]), and are recorded by [onWhatsNewShown] once the dialog has stayed uncovered for a few seconds,
     * or here as it closes, rather than as it is asked for or on its first frame: on Android it is not composed while
     * the update required screen covers the app, and Play's answer right after an update can still be the update it
     * just installed - arriving after the dialog opened, too - which puts that screen up and starts its flow, which
     * ends this process; a version recorded before anybody saw it would never be introduced. Recording it once it has
     * stayed up rather than only as it closes still keeps a process ended with the dialog up after that from
     * introducing it again.
     * Keeping every introduced version also makes rolling back and returning to a version silent.
     * An empty release message is recorded at once, so a small release introduces nothing.
     */
    suspend fun showWhatsNewOnVersionChange() {
        if (isFirstLaunch.await()) return
        val preferences = userPreferencesState.first { it !is DataState.Loading }.data ?: return
        if (CAMPFIRE_VERSION_NAME in preferences.seenWhatsNewVersions) return
        // An onboarding still owed is opened instead, and it introduces the app as it is now.
        if (preferences.isOnboardingPending) return recordWhatsNewVersion()
        awaitAppOnScreen()
        if (LocalizedStrings.get(Res.string.whats_new_message).isNotBlank()) {
            combine(dialogHost.visibleDialog, importController.isImporting, importReport, importController.queuedImportCount) { dialog, isImporting, report, queuedImportCount ->
                canShowWhatsNew(
                    hasDialog = dialog != null,
                    isImporting = isImporting,
                    hasImportReport = report != null,
                    queuedImportCount = queuedImportCount,
                )
            }.first { it }
            if (dialogHost.showIfNoneIsShown(DialogType.WhatsNew)) {
                // A close before the dialog's own delay ran out; being covered leaves the dialog where it is.
                dialogHost.visibleDialog.first { it != DialogType.WhatsNew }
                recordWhatsNewVersion()
            }
        } else {
            recordWhatsNewVersion()
        }
    }

    /** What [DialogType.WhatsNew] calls once it has stayed on screen for a moment, see [showWhatsNewOnVersionChange]. */
    fun onWhatsNewShown() {
        scope.launch { recordWhatsNewVersion() }
    }

    private suspend fun recordWhatsNewVersion() {
        try {
            withContext(NonCancellable) {
                updateUserPreferences { it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME) }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not remember the introduced version: ${exception.message}")
        }
    }

    /**
     * Null where the bundled files could not be read, which each caller then says as much about as it should. On the
     * web they are requests to the site, and one that neither answers nor fails would otherwise hold whatever waits for
     * it for good - on a first run, the launch screen, which only goes once the demo has been planted or given up on.
     */
    private suspend fun readDemoLibrary() = try {
        withTimeoutOrNull(DEMO_LIBRARY_READ_TIMEOUT_MILLIS) { DemoLibrary.read() }
            .also { if (it == null) println("Could not read the demo library in time.") }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read the demo library: ${exception.message}")
        null
    }

    private companion object {
        const val DEMO_LIBRARY_READ_TIMEOUT_MILLIS = 10_000L // Past the drawables' five seconds: it cuts short a first impression, not a frame.
    }
}
