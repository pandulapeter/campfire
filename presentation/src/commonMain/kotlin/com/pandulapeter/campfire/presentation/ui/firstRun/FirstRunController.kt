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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
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
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.settings.DemoLibraryOffer
import com.pandulapeter.campfire.presentation.ui.state.ImportController
import com.pandulapeter.campfire.presentation.ui.state.ImportController.ImportRequest
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
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's start: the demo library planted on a first run, the welcome sheet, What's new, the place an address asked
 * the app to open on, and what the launch screen waits for before it goes.
 *
 * @param isFirstLaunch Whether this is the first launch of this installation, see `CampfireViewModel.isFirstLaunch`.
 * @param isEditorDraftRecoveryPending Whether a draft a previous run left is still being reopened.
 * @param editorDraftRecovery Completed with whether the editor was reopened on a stored draft.
 * @param restoreNavigationState See `CampfireViewModel.restoreNavigationState`.
 * @param selectSettingsTab Opens a tab of the settings screen.
 */
internal class FirstRunController(
    private val scope: CoroutineScope,
    private val isFirstLaunch: Deferred<Boolean>,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val importController: ImportController,
    private val backStack: List<CampfireDestination>,
    private val screenData: StateFlow<DataState<ScreenData>>,
    private val userPreferencesState: StateFlow<DataState<UserPreferences>>,
    isEditorDraftRecoveryPending: StateFlow<Boolean>,
    private val editorDraftRecovery: Deferred<Boolean>,
    private val syncProviders: List<SyncProviderId>,
    private val songFilter: StateFlow<SongFilter>,
    private val getScreenData: GetScreenDataUseCase,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val restoreNavigationState: (NavigationState) -> Boolean,
    private val selectTopLevelDestination: (CampfireDestination.TopLevel) -> Unit,
    private val selectSettingsTab: (SettingsTab) -> Unit,
) {

    /**
     * True until the app has settled whether it is planting the demo library, and until it has finished if it is,
     * see [plantDemoLibraryOnFirstRun]. It starts out true rather than being set when the planting begins, because
     * that decision takes a read of its own and the scan of the library can finish first: an empty library would
     * then be announced as the answer, a moment before the songs on their way into it arrived.
     */
    private val isDemoLibraryPending = MutableStateFlow(true)

    /**
     * Completed once the first run has planted the demo library or found no reason to, which the consumer of
     * [importQueue] waits for before it takes its first batch: a file opened with the app as it starts for the first
     * time is queued long before that decision, and it belongs in a library that already has the demo in it rather
     * than the other way round - where the file has a demo song's name, the question about it is then asked of the
     * file the user opened, and not of songs they never asked for.
     */
    val demoLibraryDecision = CompletableDeferred<Unit>()

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
     * Latched like [arePreferencesLoaded]: a rescan reads the library again and says so, and none of that is a reason
     * to put the launch screen back up over an app the user is already using.
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
     * [hasShownApp] as something to wait for, which the welcome sheet does, see [showWelcomeOnFirstRun], and as
     * something to watch, which the store's update check does, see `rememberAppUpdateController`.
     */
    private val _isAppOnScreen = MutableStateFlow(false)
    val isAppOnScreen: StateFlow<Boolean> = _isAppOnScreen.asStateFlow()

    /** True from the moment the demo library is asked for until the offer has caught up with the outcome, see [importDemoLibrary]. */
    private val isAddingDemoLibrary = MutableStateFlow(false)

    /**
     * The settings screen's offer to add the demo library, null for as long as there is nothing to offer: while the
     * library holds all of it, and until the library has been read, like [librarySummary] and for the same reason -
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
     * Opens the app on the place [resolve] picks from the library, once the library has been read - the web build's
     * address, which can name a song or a setlist that only the library can say is still there. The launch screen is
     * held until then, see [isLaunchNavigationPending]. Only the first call counts, since only one start up can be
     * answered, and the answer is only taken while the app is still where every start opens: coming back from a
     * consent page opens Settings on its own, and that is the screen the user is waiting for. [resolve] is told whether
     * performance mode is on as the preferences were read rather than as [isPerformanceModeEnabled] says, since that
     * state may not have caught up with the read yet, and the mode decides whether an editor may be opened at all.
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
            importController.enqueueImport(files, isDemoLibrary = true).await()
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
                                files = files,
                                shouldAnnounceResult = false,
                                shouldOpenSong = false,
                                isDemoLibrary = true,
                            ),
                        )
                        importController.awaitImportSettled()
                    }
                }
                // Before the preferences are written rather than after: neither the queue nor the launch screen has any
                // reason to wait for those.
                demoLibraryDecision.complete(Unit)
                isDemoLibraryPending.update { false }
                // Read and written by the repository rather than copied from userPreferencesState, which can lag it by a
                // dispatch and would write the demo record the import just took back out. With nothing read there is
                // nothing to write either, and the next start is a first run again - which plants nothing into a library
                // that has songs in it.
                updateUserPreferences { it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME) }
            }
        } finally {
            // In a finally rather than at the end: whatever went wrong, the app is no longer waiting for this, and
            // the launch screen is over the whole of it - and neither is the queue of imports.
            demoLibraryDecision.complete(Unit)
            isDemoLibraryPending.update { false }
        }
    }

    /**
     * Puts [DialogType.Welcome] over the app on the first run, once the launch screen has gone: the sheet is a window of
     * its own on Android and would otherwise slide up over the mark rather than over the library it introduces, which
     * is also what lets the colors picked in it be seen taking hold of the app behind it. Only onto a screen with no
     * other dialog on it and no import screen ([ImportReport]) under it - on a first run that is the question about a
     * file the app was opened with, which is a screen of its own rather than a dialog - since a welcome over a question
     * would leave it unanswered, its Open settings taking the import screen off the stack and the import with it. It is
     * skipped rather than put up later: one that waited for the answer would arrive in the middle of whatever the user
     * went on to do next. Showing it is the only time it is shown: the first run's preferences
     * are written as the demo library is settled, before this, so a process that ends with the sheet still up starts
     * the next time without it.
     */
    suspend fun showWelcomeOnFirstRun() {
        if (!isFirstLaunch.await()) return
        isAppOnScreen.first { it }
        // A report is set before it is pushed, so this also covers one still waiting for its push.
        if (!canShowWelcome(hasDialog = dialogHost.visibleDialog.value != null, hasImportReport = importController.importReport.value != null)) return
        dialogHost.showIfNoneIsShown(DialogType.Welcome)
    }

    /**
     * The first installed version belongs to the welcome, so it is recorded by [plantDemoLibraryOnFirstRun] instead.
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
        isAppOnScreen.first { it }
        if (LocalizedStrings.get(Res.string.whats_new_message).isNotBlank()) {
            combine(dialogHost.visibleDialog, importController.isImporting, importController.importReport, importController.queuedImportCount) { dialog, isImporting, report, queuedImportCount ->
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
     * The welcome sheet's way on to the rest of the settings, on the tab holding sync where this build has any: that
     * is the one thing the sheet points at that nothing else in the app would lead a new user to. The sheet closes
     * itself, since only it can do that with the animation it closes with everywhere else.
     */
    fun openSettingsFromWelcome() {
        if (backStack.lastOrNull() != CampfireDestination.Settings) selectTopLevelDestination(CampfireDestination.Settings)
        selectSettingsTab(if (syncProviders.isEmpty()) SettingsTab.GENERAL else SettingsTab.LIBRARY)
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
