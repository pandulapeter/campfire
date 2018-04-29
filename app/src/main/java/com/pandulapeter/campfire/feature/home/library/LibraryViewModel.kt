package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.normalize
import com.pandulapeter.campfire.util.onTextChanged
import com.pandulapeter.campfire.util.swap

class LibraryViewModel(
    context: Context,
    val toolbarTextInputView: ToolbarTextInputView,
    private val updateSearchToggleDrawable: (Boolean) -> Unit,
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit
) : SongListViewModel(context) {

    private val newString = context.getString(R.string.new_tag)
    private val popularString = context.getString(R.string.popular_tag)
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
            }
        }
    var shouldShowDownloadedOnly = preferenceDatabase.shouldShowDownloadedOnly
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowDownloadedOnly = value
                updateAdapterItems()
            }
        }
    var shouldShowExplicit = preferenceDatabase.shouldShowExplicitLibrary
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowExplicitLibrary = value
                updateAdapterItems()
            }
        }
    var sortingMode = SortingMode.fromIntValue(preferenceDatabase.librarySortingMode)
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.librarySortingMode = value.intValue
                updateAdapterItems(true)
            }
        }
    var disabledLanguageFilters = preferenceDatabase.disabledLibraryLanguageFilters
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.disabledLibraryLanguageFilters = value
                updateAdapterItems(true)
            }
        }
    var languages = mutableListOf<Language>()
    var shouldSearchInTitles = preferenceDatabase.shouldSearchInTitles
        set(value) {
            field = value
            updateAdapterItems(true)
        }
    var shouldSearchInArtists = preferenceDatabase.shouldSearchInArtists
        set(value) {
            field = value
            updateAdapterItems(true)
        }

    init {
        toolbarTextInputView.apply {
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_LIBRARY
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        super.onSongRepositoryDataUpdated(data)
        if (data.isNotEmpty()) {
            languages.swap(songRepository.languages)
            onDataLoaded(languages)
        }
    }

    override fun onListUpdated(items: List<SongListItemViewModel>) {
        super.onListUpdated(items)
        if (librarySongs.toList().isNotEmpty()) {
            placeholderText.set(R.string.library_placeholder)
            buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.library_filters)
            buttonIcon.set(R.drawable.ic_filter_and_sort_24dp)
        }
    }

    override fun onActionButtonClicked() {
        if (buttonIcon.get() == 0) {
            updateData()
        } else {
            openSecondaryNavigationDrawer()
        }
    }

    override fun Sequence<Song>.createViewModels() = filterByQuery()
        .filterDownloaded()
        .filterByLanguage()
        .filterExplicit()
        .sort()
        .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
        .toMutableList<SongListItemViewModel>()
        .apply {
            val headerIndices = mutableListOf<Int>()
            val songsOnly = filterIsInstance<SongListItemViewModel.SongViewModel>().map { it.song }
            songsOnly.forEachIndexed { index, song ->
                if (when (sortingMode) {
                        SortingMode.TITLE -> index == 0 || song.getNormalizedTitle()[0] != songsOnly[index - 1].getNormalizedTitle()[0]
                        SortingMode.ARTIST -> index == 0 || song.artist != songsOnly[index - 1].artist
                        SortingMode.POPULARITY -> songsOnly[0].isNew && (index == 0 || songsOnly[index].isNew != songsOnly[index - 1].isNew)
                    }
                ) {
                    headerIndices.add(index)
                }
            }
            (headerIndices.size - 1 downTo 0).forEach { position ->
                val index = headerIndices[position]
                add(
                    index, SongListItemViewModel.HeaderViewModel(
                        when (sortingMode) {
                            SortingMode.TITLE -> songsOnly[index].getNormalizedTitle()[0].toString().toUpperCase()
                            SortingMode.ARTIST -> songsOnly[index].artist
                            SortingMode.POPULARITY -> if (!songsOnly[0].isNew) "" else if (songsOnly[index].isNew) newString else popularString
                        }
                    )
                )
            }
        }

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
    }

    fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                updateSearchToggleDrawable(toolbarTextInputView.isTextInputVisible)
                if (shouldScrollToTop) {
                    updateAdapterItems(!isTextInputVisible)
                }
                buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.library_filters)
            }
        }
    }

    //TODO: Prioritize results that begin with the searchQuery.
    private fun Sequence<Song>.filterByQuery() = if (toolbarTextInputView.isTextInputVisible) {
        query.trim().normalize().let { query ->
            filter {
                (it.getNormalizedTitle().contains(query, true) && shouldSearchInTitles) || (it.getNormalizedArtist().contains(query, true) && shouldSearchInArtists)
            }
        }
    } else this

    private fun Sequence<Song>.filterDownloaded() = if (shouldShowDownloadedOnly) filter { songDetailRepository.isSongDownloaded(it.id) } else this

    private fun Sequence<Song>.filterByLanguage() = filter { !disabledLanguageFilters.contains(it.language ?: Language.Unknown.id) }

    private fun Sequence<Song>.filterExplicit() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    private fun Sequence<Song>.sort() = when (sortingMode) {
        SortingMode.TITLE -> sortedBy { it.getNormalizedArtist() }.sortedBy { it.getNormalizedTitle() }
        SortingMode.ARTIST -> sortedBy { it.getNormalizedTitle() }.sortedBy { it.getNormalizedArtist() }
        SortingMode.POPULARITY -> sortedBy { it.getNormalizedArtist() }.sortedBy { it.getNormalizedTitle() }.sortedByDescending { it.popularity }.sortedByDescending { it.isNew }
    }

    enum class SortingMode(val intValue: Int) {
        TITLE(0),
        ARTIST(1),
        POPULARITY(2);

        companion object {
            fun fromIntValue(value: Int) = SortingMode.values().find { it.intValue == value } ?: TITLE
        }
    }
}