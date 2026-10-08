/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.BACK_STACK_KEY
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * Where the user is: the back stack, which every way of moving through the app changes, the settings tab, the page
 * each song details screen is on, and the scroll positions the top level screens keep while they are away. What
 * else a change of the back stack does - stopping a click, ending the setlist reorder mode - is registered by the
 * owner as a listener, see [addOnBackStackChanged].
 *
 * @param userPreferences The preferences as the repository has read them, which may be ahead of the view model's.
 * @param hasUnsavedEditorText Whether the editor holds text that is not written yet, as of this moment.
 * @param hasUnsavedEditorChanges Whether the editor holds text that is not written yet, as its state says.
 * @param onTransitionEnded Called once a navigation transition has ended, see [setNavigationTransitionRunning].
 */
internal class Navigator(
    private val savedStateStore: SavedStateStore,
    private val dialogHost: DialogHost,
    private val songsSearch: () -> SearchState,
    private val setlistsSearch: () -> SearchState,
    private val importReportSearch: () -> SearchState,
    private val topLevelDestinations: () -> List<CampfireDestination.TopLevel>,
    private val userPreferences: () -> UserPreferences?,
    private val hasUnsavedEditorText: () -> Boolean,
    private val hasUnsavedEditorChanges: () -> Boolean,
    private val isSetlistReordering: () -> Boolean,
    private val endSetlistReordering: () -> Unit,
    private val onTransitionEnded: () -> Unit,
) {

    /** Written into [savedStateStore] on every change, see [persistBackStack], and read back from it here. */
    val backStack: SnapshotStateList<CampfireDestination> = mutableStateListOf<CampfireDestination>().apply {
        // The import screen shows what an import running in this process is doing or did, so a new process has
        // nothing to put on it and comes back on the screen under it.
        addAll(
            savedStateStore.restore<List<CampfireDestination>>(BACK_STACK_KEY)
                ?.filterNot { it == CampfireDestination.ImportReport }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(CampfireDestination.Songs),
        )
    }

    /**
     * Bumped whenever the back stack changes while a navigation transition is still running. The UI puts it into
     * the metadata of every entry, which makes the new scene differ from the one the running transition started
     * from: Navigation 3 then retargets the running animation instead of taking its "predictive back cancelled"
     * path, which cannot handle an interrupted animation and leaves the UI stuck halfway.
     *
     * Not bumped by the pop that completes a predictive back gesture, although the transition it seeked is running
     * then: that path is the one Navigation 3 finishes such a gesture with, and only while the new scene is the very one
     * the gesture seeked towards. A different one starts a second animation towards a scene of the same key, whose
     * screens go from visible to visible - and whatever they animate on their own enter transition, the scrim of the
     * screen being returned to among them, stays where the gesture left it.
     */
    var navigationGeneration by mutableIntStateOf(0)
        private set

    private var isNavigationTransitionRunning = false

    /**
     * Where each of the three top level screens is scrolled to - the settings screen once per tab, since each of its
     * tabs scrolls on its own - kept here because a tab that is left is taken off the back stack and loses everything
     * it remembered with it, see [ScrollPosition].
     */
    val metronomeScrollPosition = ScrollPosition()

    val settingsScrollPositions = SettingsTab.entries.associateWith { ScrollPosition() }

    /**
     * Which tab of the settings screen is open, kept here so that the screen's own recompositions and the sync consent
     * can reach it. Like the other two screens' scroll positions it lasts for the session; unlike them, the tabs' scroll
     * is thrown away when Settings is selected from another top level screen. The screen only reads it as it is composed, but it is a state because the web build's address names the tab, and follows it.
     */
    var settingsTab by mutableStateOf(SettingsTab.GENERAL)

    /**
     * Whether a way back out of the settings screen goes to its General tab rather than leaving it: the tabs are the
     * first thing on the screen, and General is the one it opens on, so a Back from any other one is taken as a step back
     * through them before it is a step back to the songs.
     */
    val isSettingsBackToGeneral
        get() = backStack.lastOrNull() == CampfireDestination.Settings && settingsTab != SettingsTab.GENERAL

    /**
     * The song each song details screen on the back stack has settled on, by [CampfireDestination.SongDetails.id]: the
     * pager is the screen's own, and its page is the one thing about where the user is that the destination does not
     * say. Reported by the screen, see [onSongDetailsPageSettled], and read by [navigationState].
     */
    val songDetailsCurrentSongs = mutableStateMapOf<String, String>()

    /**
     * The search a back gesture is about, which is the one belonging to the screen that is on top. Only the two list
     * screens have one that opens and closes, and a search left open on a list screen is no business of the song that
     * was opened from it: there, back is back. The import screen's field is always there, so there is nothing for a
     * back gesture to close before the screen.
     *
     * The screens answer their own back gesture with a navigation event handler registered inside them, which is
     * composed after the navigation's own and therefore wins on its own; this is here for the desktop, whose window
     * key handler decides what Escape means from outside the composition entirely.
     */
    val currentSearch: SearchState?
        get() = when (backStack.lastOrNull()) {
            CampfireDestination.Songs -> songsSearch()
            CampfireDestination.Setlists -> setlistsSearch()
            else -> null
        }

    /** Emitted when the item of the top level screen that is already open is pressed; that screen scrolls to its top. */
    private val _scrollToTopRequests = MutableSharedFlow<CampfireDestination.TopLevel>(extraBufferCapacity = 1)
    val scrollToTopRequests = _scrollToTopRequests.asSharedFlow()

    /**
     * Answers Ctrl / Cmd + F, which the desktop window and the web page both hear before anything in the composition
     * does: nothing on a list screen is focused while its search is closed, and a key event only travels along the
     * focus path. It opens the search of the list screen that is on top, or brings the caret back into it if it is
     * open already - the import screen's field, which is always there, being given the caret the same way - and
     * answers whether it did, so that the key is left to whoever else wants it everywhere else - the
     * browser's own find bar among them. A dialog, a sheet or an overflow menu keeps it from reaching the screen under
     * it, the way it keeps Escape from reaching it.
     */
    fun openCurrentSearch(): Boolean {
        if (dialogHost.visibleDialog.value != null || dialogHost.overlayState.isAnyMenuOpen || isSetlistReordering()) return false
        val search = currentSearch
            ?: importReportSearch().takeIf { backStack.lastOrNull() == CampfireDestination.ImportReport }
            ?: return false
        search.openOrFocus()
        return true
    }

    /** Reported by the UI whenever the state of the navigation transition changes, see [navigationGeneration]. */
    fun setNavigationTransitionRunning(isRunning: Boolean) {
        val hasTransitionEnded = isNavigationTransitionRunning && !isRunning
        isNavigationTransitionRunning = isRunning
        if (hasTransitionEnded) onTransitionEnded()
    }

    /** Changes the back stack, then tells every listener what was on top before and what the stack is now, in order. */
    fun updateBackStack(
        isPredictiveBackCompleted: Boolean = false,
        update: SnapshotStateList<CampfireDestination>.() -> Unit,
    ) {
        if (isNavigationTransitionRunning && !isPredictiveBackCompleted) navigationGeneration++
        val previousTop = backStack.lastOrNull()
        backStack.update()
        backStackListeners.forEach { it(previousTop, backStack) }
        persistBackStack()
    }

    private val backStackListeners = mutableListOf<(previousTop: CampfireDestination?, stack: List<CampfireDestination>) -> Unit>()

    /** Called after every change to [backStack], in the order the listeners were added, before it is saved. */
    fun addOnBackStackChanged(listener: (previousTop: CampfireDestination?, stack: List<CampfireDestination>) -> Unit) {
        backStackListeners += listener
    }

    /**
     * Called after every change to [backStack]. The state Navigation 3 saves for each entry (the editor's text, a
     * scroll position) is saved with the Activity regardless, but it is only ever handed back to an entry with the
     * same content key, so a stack that restarted on the Songs screen would leave all of it behind unclaimed.
     *
     * A stack whose JSON would not fit [MAX_SAVED_BACK_STACK_LENGTH] is saved only up to the screen that makes it too
     * long - in practice a song opened from a setlist of thousands, which names every one of them. A restored process
     * then comes back one screen short, which beats one that crashes as it is sent to the background: the saved state
     * crosses to the system in a single transaction of at most a megabyte.
     */
    fun persistBackStack() {
        val stack = backStack.toList()
        // The stack is a few screens deep at most, so encoding it once per screen is nothing, and the first one fits
        // in every case but the pathological one.
        savedStateStore.persistJson(
            BACK_STACK_KEY,
            (stack.size downTo 1).asSequence()
                .map { Json.encodeToString<List<CampfireDestination>>(stack.subList(0, it)) }
                .firstOrNull { it.length <= MAX_SAVED_BACK_STACK_LENGTH }
                ?: Json.encodeToString<List<CampfireDestination>>(listOf(CampfireDestination.Songs)),
        )
    }

    /** Reported by the song details screen whenever its pager comes to rest, see [songDetailsCurrentSongs]. */
    fun onSongDetailsPageSettled(destination: CampfireDestination.SongDetails, songFileName: String) {
        if (backStack.any { it is CampfireDestination.SongDetails && it.id == destination.id }) {
            songDetailsCurrentSongs[destination.id] = songFileName
        }
    }

    /** The file name of the song the given details screen is showing, which is where it was opened until it is paged. */
    fun currentSongFileName(destination: CampfireDestination.SongDetails) =
        songDetailsCurrentSongs[destination.id] ?: destination.songFileNames.getOrNull(destination.initialIndex) ?: destination.songFileNames.firstOrNull()

    /**
     * Where the user is right now, see [NavigationState]. Its snapshot states can be observed with snapshotFlow, and the
     * two searches, which are flows, have to be combined in by whoever observes it.
     */
    val navigationState: NavigationState
        get() = NavigationState(
            backStack = backStack.map { destination ->
                if (destination is CampfireDestination.SongDetails) {
                    destination.copy(initialIndex = destination.songFileNames.indexOf(currentSongFileName(destination)).takeIf { it >= 0 } ?: destination.initialIndex)
                } else {
                    destination
                }
            },
            isSongsSearchOpen = songsSearch().isOpen.value,
            isSetlistsSearchOpen = setlistsSearch().isOpen.value,
            settingsTab = settingsTab,
        )

    /**
     * Takes the user to [state] in one step, which is how the web build follows an address it was opened on or the
     * browser's Forward button. Refused, returning false, while the editor holds unsaved text: nothing but the
     * editor's own ways out may take that text off the screen, and those ask first. A search it opens is reopened on the
     * text its field still holds, since this is stepping back to a place rather than asking anything new. A screen of a
     * feature switched off is cut off with everything above it ([withoutDisabledFeatures]), so an address naming one
     * opens what is under it, and the browser's address is then written over with that.
     */
    fun restoreNavigationState(state: NavigationState): Boolean {
        if (hasUnsavedEditorText()) return false
        // Read from the repository's own state, which the launch has waited for, rather than from userPreferences,
        // which may not have caught up with the read yet.
        val preferences = userPreferences()
        val allowedState = state.withoutDisabledFeatures(
            areSetlistsEnabled = preferences?.areSetlistsEnabled != false,
            isMetronomeEnabled = preferences?.isMetronomeEnabled != false,
        )
        if (allowedState.backStack.isEmpty()) return false
        settingsTab = allowedState.settingsTab
        listOf(songsSearch() to allowedState.isSongsSearchOpen, setlistsSearch() to allowedState.isSetlistsSearchOpen).forEach { (search, isOpen) ->
            if (isOpen != search.isOpen.value) if (isOpen) search.reopen() else search.close()
        }
        if (backStack.toList() != allowedState.backStack) {
            updateBackStack {
                clear()
                addAll(allowedState.backStack)
            }
        }
        return true
    }

    /**
     * Rebuilds the stack around a top level screen. Refused while an editor on the stack holds unsaved text: the
     * navigation chrome that calls this is hidden over the editor, and nothing else may take that text off the screen
     * without asking, see [navigateBack]. Refused too for a screen whose feature is switched off: its item stays on
     * screen, and tappable, for as long as it takes to shrink away.
     */
    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
        if (destination !in topLevelDestinations()) return
        if (backStack.lastOrNull() == destination) {
            // Pressing the item of the screen that is already open takes that screen back to its resting state.
            if (destination != CampfireDestination.Settings) currentSearch?.close()
            _scrollToTopRequests.tryEmit(destination)
            return
        }
        if (hasUnsavedEditorText() && backStack.any { it is CampfireDestination.SongEditor }) return
        // Settings keeps its tab for the session but is always scrolled to the top of it on arrival; the other two screens
        // keep where they were left.
        if (destination == CampfireDestination.Settings) {
            settingsScrollPositions.values.forEach { it.offset = 0 }
        }
        updateBackStack {
            clear()
            add(CampfireDestination.Songs)
            if (destination != CampfireDestination.Songs) {
                add(destination)
            }
        }
    }

    fun openSong(song: Song) = openSongDetails(
        CampfireDestination.SongDetails(songFileNames = listOf(song.fileName), setlistFileName = null, initialIndex = 0)
    )

    /**
     * The pager can only page through the songs that are actually there, so the index is taken from those rather
     * than from the position of the row in the setlist, which also counts the entries whose file is missing.
     */
    fun openSongInSetlist(setlistWithSongs: SetlistWithSongs, song: Song) = openSongDetails(
        CampfireDestination.SongDetails(
            songFileNames = setlistWithSongs.songs.map { it.fileName },
            setlistFileName = setlistWithSongs.setlist.fileName,
            initialIndex = setlistWithSongs.songs.indexOfFirst { it.fileName == song.fileName }.coerceAtLeast(0),
        )
    )

    private fun openSongDetails(destination: CampfireDestination.SongDetails) {
        if (backStack.lastOrNull() !is CampfireDestination.SongDetails) {
            updateBackStack { add(destination) }
        }
    }

    /**
     * Where a file opened with the app lands. It is put on top of whatever is on screen, so that Back returns there,
     * except over a song that is already open, which it takes the place of rather than stacking a second pager on.
     * An editor is covered like any other screen as long as everything in it is saved, and Back returns to it. One
     * holding unsaved text is left alone, as [selectTopLevelDestination] leaves it: the song's arrival is announced all
     * the same, and the unsaved changes question is only ever asked of an editor on top, so the text would be one
     * closed window away from being lost without it.
     */
    fun openImportedSong(fileName: String) {
        if (hasUnsavedEditorText() && backStack.any { it is CampfireDestination.SongEditor }) return
        val songFileNames = listOf(fileName)
        val current = backStack.lastOrNull()
        if (current is CampfireDestination.SongDetails && current.setlistFileName == null && current.songFileNames == songFileNames) return
        updateBackStack {
            if (current is CampfireDestination.SongDetails) removeAt(lastIndex)
            add(CampfireDestination.SongDetails(songFileNames = songFileNames, setlistFileName = null, initialIndex = 0))
        }
    }

    /**
     * A song of the import screen's list, opened in a pager over the songs of the group it was listed in, so that what an
     * import brought can be read through one after the other and Back returns to the list.
     */
    fun openReportedSong(songFileNames: List<String>, index: Int) {
        val (pages, initialIndex) = reportedSongPages(songFileNames, index)
        openSongDetails(CampfireDestination.SongDetails(songFileNames = pages, setlistFileName = null, initialIndex = initialIndex))
    }

    /**
     * Every way out of a screen ends up here - the app bar's button, the system's back gesture and the desktop
     * window's Escape key - which is why this is where the editor's unsaved text is caught: nothing the user typed
     * is thrown away without being asked about it first, and why a settings tab other than General goes back to that
     * one before the screen is left ([isSettingsBackToGeneral]).
     *
     * @param isPredictiveBackCompleted Whether this is the pop a predictive back gesture ends in, see
     *   [navigationGeneration].
     */
    fun navigateBack(isPredictiveBackCompleted: Boolean = false) {
        when {
            isSetlistReordering() -> endSetlistReordering()
            hasUnsavedEditorChanges() && backStack.lastOrNull() is CampfireDestination.SongEditor -> {
                dialogHost.showDialog(DialogType.UnsavedChanges)
            }
            isSettingsBackToGeneral -> settingsTab = SettingsTab.GENERAL
            else -> popBackStack(isPredictiveBackCompleted)
        }
    }

    fun popBackStack(isPredictiveBackCompleted: Boolean = false) {
        if (backStack.size > 1) {
            updateBackStack(isPredictiveBackCompleted = isPredictiveBackCompleted) { removeAt(lastIndex) }
        }
    }

    private companion object {
        const val MAX_SAVED_BACK_STACK_LENGTH = 100_000 // Characters of JSON, about 200 KB as the UTF-16 a Bundle writes.
    }
}
