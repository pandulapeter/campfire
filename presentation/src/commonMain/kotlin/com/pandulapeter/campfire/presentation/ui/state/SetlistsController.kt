/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.EditSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateSetlistUseCase
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistDetails
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.screens.setlists.details
import com.pandulapeter.campfire.presentation.ui.screens.setlists.mergedSetlistDetails
import com.pandulapeter.campfire.presentation.ui.screens.setlists.withSongTicked
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.SETLISTS_SEARCH_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The setlists screen: its search, its scroll position and the reorder mode, what the list shows, and every change
 * made to a setlist.
 *
 * @param isImporting Whether an import is running, which an empty library is waiting for.
 * @param followReportedFileNames Keeps the import screen's result naming what the library holds, see
 *   `CampfireViewModel.followReportedFileNames`.
 * @param changeUserPreferences See [PreferencesController.changeUserPreferences].
 */
internal class SetlistsController(
    private val scope: CoroutineScope,
    savedStateStore: SavedStateStore,
    private val backStack: List<CampfireDestination>,
    userPreferences: StateFlow<UserPreferences?>,
    private val setlists: StateFlow<List<Setlist>>,
    private val allSongs: StateFlow<List<Song>>,
    indexedSongs: StateFlow<LibraryState.IndexedSongs>,
    screenData: StateFlow<DataState<ScreenData>>,
    isImporting: StateFlow<Boolean>,
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val createSetlist: CreateSetlistUseCase,
    private val saveSetlist: SaveSetlistUseCase,
    private val editSetlist: EditSetlistUseCase,
    private val updateSetlist: UpdateSetlistUseCase,
    private val deleteSetlist: DeleteSetlistUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
    private val followReportedFileNames: ((String) -> String?) -> Unit,
    private val changeUserPreferences: (UserPreferences.() -> UserPreferences) -> Unit,
) {

    val setlistsScrollPosition = ScrollPosition()

    val setlistsSearch = savedStateStore.restoreSearch(SETLISTS_SEARCH_KEY)

    /** Transient mode shared with desktop Escape and browser history; never restored after leaving the screen. */
    var reorderingSetlistFileName by mutableStateOf<String?>(null)

    val isSetlistReordering: Boolean
        get() = backStack.lastOrNull() == CampfireDestination.Setlists && reorderingSetlistFileName != null

    private val shouldShowArchivedSetlists = userPreferences.map { it?.shouldShowArchivedSetlists == true }.distinctUntilChanged()

    /**
     * The setlists the screen would list if nothing had been typed into its search, with every song they name: the
     * song filters are about the song list and a setlist is answerable to nobody but whoever wrote it down, so a
     * setlist shows what it holds whether or not the library screen next door is narrowed to something else. The
     * archived ones are the one thing left out, and only until the user asks for them, see
     * [UserPreferences.shouldShowArchivedSetlists].
     *
     * Kept apart from the search below so that the placeholder can tell a setlist list emptied by the archive filter
     * from one emptied by the search, the way the song list tells its own two empty states apart - and a state
     * rather than a plain flow because both the placeholder and the search below read it.
     */
    private val visibleSetlists = combine(setlists, indexedSongs, shouldShowArchivedSetlists) { setlists, indexed, shouldShowArchivedSetlists ->
        val songsByFileName = indexed.search.byFileName
        setlists.filter { shouldShowArchivedSetlists || !it.isArchived }.map { setlist ->
            SetlistWithSongs(
                setlist = setlist,
                entries = setlist.entries.mapIndexed { index, entry ->
                    when (val song = songsByFileName[entry.songFileName]) {
                        null -> SetlistWithSongs.Entry.Missing(index = index, songFileName = entry.songFileName)
                        else -> SetlistWithSongs.Entry.Present(index = index, song = song.song)
                    }
                },
            )
        }
    }.flowOn(Dispatchers.Default).asState(scope, emptyList())

    /**
     * The setlists as the screen lists them, narrowed by its search: a setlist answers it by what it says about
     * itself - its title and its description - or by holding a song that does.
     *
     * A setlist that answers is shown **whole**. The search finds setlists rather than songs inside them: a setlist
     * is the list somebody wrote down, and three of its twelve songs is not that list.
     */
    val setlistsWithSongs = combine(visibleSetlists, indexedSongs, setlistsSearch.activeQuery) { setlists, indexed, query ->
        // Branched on the folded query, as the song list is: one of punctuation or symbols alone is no search.
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isEmpty()) {
            setlists
        } else {
            setlists.filter {
                it.setlist.matchesSearch(normalizedQuery = normalizedQuery, songs = indexed.search.byFileName, normalizeSearchText = normalizeSearchText)
            }
        }
    }.flowOn(Dispatchers.Default).asState(scope, emptyList())

    /**
     * What the setlists screen shows instead of setlists, null while it has some. Same reasoning as
     * [songsPlaceholder]: without it a load in progress is indistinguishable from a user who has no setlists, and
     * the screen claims there are none for as long as reading the library takes. A library whose every setlist is
     * archived is told apart from one with no setlists at all, and from one whose setlists the search did not
     * match, for the same reason the song list tells its empty states apart: each of the three is answered by
     * something different, and only one of them by making a setlist.
     */
    val setlistsPlaceholder = combine(screenData, setlistsWithSongs, visibleSetlists, isImporting) { screenData, setlistsWithSongs, visibleSetlists, isImporting ->
        setlistListPlaceholder(screenData = screenData, setlistsWithSongs = setlistsWithSongs, visibleSetlists = visibleSetlists, isImporting = isImporting)
    }.asState(scope, Placeholder.LOADING)

    /**
     * A new setlist is followed by the song picker for it, since a setlist is created to have songs put into it and
     * the setlists screen offers no other way of doing that in one place. The picker only opens where nothing else
     * has been opened while the file was being written, and only where the library has songs to pick from.
     */
    fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = messageSink.launchLibraryChange {
        val setlist = createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
        if (allSongs.value.isNotEmpty()) {
            dialogHost.showIfNoneIsShown(DialogType.SongPicker(setlist))
        }
    }

    /**
     * Creating a setlist from the setlist picker of one song, where the only reason it is being created at that
     * moment is that the song should go into it. Both happen in the same library change, so the picker's tick is
     * already there when the new setlist appears in it.
     */
    fun createSetlistWithSong(title: String, description: String, date: LocalDate, isCountdownShown: Boolean, songFileName: String) = messageSink.launchLibraryChange {
        saveSetlist(
            createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
                .copy(entries = listOf(Setlist.Entry(songFileName = songFileName))),
        )
    }

    fun addSongToSetlist(songFileName: String, setlistFileName: String) = messageSink.launchLibraryChange {
        updateEditableSetlist(setlistFileName) { setlist ->
            if (setlist.entries.none { it.songFileName == songFileName }) {
                setlist.copy(entries = setlist.entries + Setlist.Entry(songFileName = songFileName))
            } else {
                setlist
            }
        }
    }

    /**
     * Ticks one song of the song picker into the setlist or out of it. Each tick is that one song and nothing else,
     * applied to the setlist as the library has it at the time of the write rather than to the picker's copy: the
     * picker is ticked a row at a time, quickly and all into the same file, and the writes go through
     * [UpdateSetlistUseCase], which makes them one at a time and in the order they were asked for, so each tick is
     * applied to what the one before it left and none of them is lost. Nothing the picker did not touch is written back
     * from its snapshot, so a song another device added to the setlist while the sheet was open, or the order it was
     * given there, stays.
     *
     * A setlist that is gone by now - deleted by a sync run while the sheet was open, or unreadable - is not brought
     * back from the sheet's copy: that copy is the setlist as it was when the sheet opened, and nothing else in the app
     * recreates a setlist by changing it.
     */
    fun setSetlistSong(setlistFileName: String, songFileName: String, isTicked: Boolean) = messageSink.launchLibraryChange {
        updateEditableSetlist(setlistFileName) { it.withSongTicked(songFileName, isTicked) } ?: messageSink.sendMessage(Message.OperationFailed)
    }

    /**
     * The title, the description, the date and its countdown are written together, since they are the whole of what
     * the user gets to say about a setlist. Only the title reaches the file name, so the setlist that comes back may be under a name
     * this one has never seen. Only the fields the sheet changed from what it [offered] are written; the rest are the
     * library's as they are now, since the dialog has held its copy since it was opened and a sync run may have
     * brought another device's description or date in since. One that is gone by now is not brought back, and saying
     * so is a failed operation rather than a sheet that closes as if it had saved.
     */
    fun editSetlist(offered: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = messageSink.launchLibraryChange {
        val current = setlists.value.firstOrNull { it.fileName == offered.fileName }
            ?: return@launchLibraryChange messageSink.sendMessage(Message.OperationFailed)
        if (current.isArchived) return@launchLibraryChange
        val details = mergedSetlistDetails(
            offered = offered.details,
            chosen = SetlistDetails(title = title, description = description, date = date, isCountdownShown = isCountdownShown),
            current = current.details,
        )
        if (details == current.details) return@launchLibraryChange
        editSetlist.invoke(
            fileName = offered.fileName,
            title = details.title,
            description = details.description,
            date = details.date,
            isCountdownShown = details.isCountdownShown,
        ) ?: messageSink.sendMessage(Message.OperationFailed)
    }

    /**
     * A copy of the setlist under a title and a date of its own: it goes through [CreateSetlistUseCase] rather than
     * through a copied file name, so the copy gets its own name and none of the original's archived state - a copy is
     * made to be worked on.
     */
    fun duplicateSetlist(setlist: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = messageSink.launchLibraryChange {
        reorderingSetlistFileName = null
        // An archived setlist is copied too, since a set that has been played is the likeliest start for the next one.
        // The sheet's copy is the setlist as it was when the sheet opened; a sync run or a song rename since then has moved
        // the entries on, and the copy is made of the setlist as it is.
        val current = setlists.value.firstOrNull { it.fileName == setlist.fileName }
            ?: return@launchLibraryChange messageSink.sendMessage(Message.OperationFailed)
        val copy = createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
        saveSetlist(copy.copy(entries = current.entries))
    }

    /** Archiving is the way a setlist that has been played is put away without the songs in it being lost. */
    fun setSetlistArchived(setlist: Setlist, isArchived: Boolean) = messageSink.launchLibraryChange {
        reorderingSetlistFileName = null
        updateSetlist(setlist.fileName) { it.copy(isArchived = isArchived) }
    }

    fun deleteSetlist(setlistFileName: String) = messageSink.launchLibraryChange {
        if (reorderingSetlistFileName == setlistFileName) reorderingSetlistFileName = null
        deleteSetlist.invoke(setlistFileName)
        followReportedFileNames { it.takeUnless { it == setlistFileName } }
    }

    /** The transposition of the song travels in the entry, so removing it takes the transposition with it. */
    fun removeSongFromSetlist(songFileName: String, setlistFileName: String) = messageSink.launchLibraryChange {
        updateEditableSetlist(setlistFileName) { setlist -> setlist.copy(entries = setlist.entries.filterNot { it.songFileName == songFileName }) }
    }

    /**
     * Writes the order a drag ended on, as one write rather than one per row the finger crossed: a move worked out
     * from [setlists], which only catches up once the previous write has been round tripped through the repository,
     * would be recomputed from an order one or more moves out of date.
     *
     * [songFileNames] is the order the screen was showing, which a sync run or another write may have overtaken by the
     * time this one runs, so it is merged into the setlist as the library has it then ([withSongOrder]).
     *
     * [onNotWritten] is called when nothing was written: the write failed, the setlist is gone, or it is archived and
     * so refused the order. A drag's screen holds the order it drew until the library agrees with it, which a write
     * that never happened would leave it waiting for.
     */
    fun reorderSetlist(setlistFileName: String, songFileNames: List<String>, onNotWritten: () -> Unit = {}) = messageSink.launchLibraryChange {
        var isWritten = false
        try {
            isWritten = updateEditableSetlist(setlistFileName) { it.withSongOrder(songFileNames) }?.isArchived == false
        } finally {
            if (!isWritten) onNotWritten()
        }
    }

    /** Read the current archived state inside the serialized update, including for already-open dialogs. */
    suspend fun updateEditableSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? =
        updateSetlist(fileName) { setlist ->
            if (setlist.isArchived) setlist else transform(setlist)
        }.also { setlist ->
            if (reorderingSetlistFileName == fileName && (setlist == null || setlist.isArchived || setlist.entries.size < 2)) {
                reorderingSetlistFileName = null
            }
        }

    fun setShouldShowArchivedSetlists(value: Boolean) = changeUserPreferences { copy(shouldShowArchivedSetlists = value) }

    fun setSetlistSortingMode(value: UserPreferences.SetlistSortingMode) = changeUserPreferences { copy(setlistSortingMode = value) }
}
