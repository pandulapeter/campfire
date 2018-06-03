package com.pandulapeter.campfire.feature.main.songs

import android.content.Context
import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.baseSongList.SongListItemViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.normalize
import com.pandulapeter.campfire.util.onTextChanged
import com.pandulapeter.campfire.util.removePrefixes
import com.pandulapeter.campfire.util.swap
import org.koin.android.ext.android.inject

class SongsViewModel(
    context: Context,
    val toolbarTextInputView: ToolbarTextInputView,
    private val updateSearchToggleDrawable: (Boolean) -> Unit,
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit
) : BaseSongListViewModel(context) {

    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_SONGS
    private val analyticsManager by inject<AnalyticsManager>()
    private val popularString = context.getString(R.string.popular_tag)
    private val newString = context.getString(R.string.new_tag)
    val shouldShowEraseButton = ObservableBoolean()
    val shouldEnableEraseButton = ObservableBoolean()
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
                trackSearchEvent()
                shouldEnableEraseButton.set(query.isNotEmpty())
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
    var shouldShowExplicit = preferenceDatabase.shouldShowExplicitSongs
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowExplicitSongs = value
                updateAdapterItems()
            }
        }
    var sortingMode = SortingMode.fromIntValue(preferenceDatabase.songsSortingMode)
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.songsSortingMode = value.intValue
                updateAdapterItems(true)
            }
        }
    var disabledLanguageFilters = preferenceDatabase.disabledLanguageFilters
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.disabledLanguageFilters = value
                updateAdapterItems(true)
            }
        }
    var languages = mutableListOf<Language>()
    var shouldSearchInTitles = preferenceDatabase.shouldSearchInTitles
        set(value) {
            field = value
            updateAdapterItems(true)
            trackSearchEvent()
        }
    var shouldSearchInArtists = preferenceDatabase.shouldSearchInArtists
        set(value) {
            field = value
            updateAdapterItems(true)
            trackSearchEvent()
        }

    init {
        toolbarTextInputView.apply {
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_SONGS
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
        if (songs.toList().isNotEmpty()) {
            placeholderText.set(R.string.songs_placeholder)
            buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.songs_filters)
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
                        SortingMode.TITLE -> {
                            val thisTitleFirstCharacter = song.getNormalizedTitle().removePrefixes()[0]
                            index == 0 || (thisTitleFirstCharacter != songsOnly[index - 1].getNormalizedTitle().removePrefixes()[0] && !thisTitleFirstCharacter.isDigit())
                        }
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
                            SortingMode.TITLE -> songsOnly[index].getNormalizedTitle().removePrefixes()[0].let { if (it.isDigit()) "0 - 9" else it.toString() }
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
                buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.songs_filters)
            }
            shouldShowEraseButton.set(isTextInputVisible)
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
        SortingMode.TITLE -> sortedBy { it.getNormalizedArtist().removePrefixes().toString() }.sortedBy { it.getNormalizedTitle().removePrefixes().toString() }
        SortingMode.ARTIST -> sortedBy { it.getNormalizedTitle().removePrefixes() }.sortedBy { it.getNormalizedArtist().removePrefixes() }
        SortingMode.POPULARITY -> sortedBy { it.getNormalizedArtist().removePrefixes() }
            .sortedBy { it.getNormalizedTitle().removePrefixes() }
            .sortedByDescending { it.popularity }.sortedByDescending { it.isNew }
    }

    private fun trackSearchEvent() {
        if (query.isNotEmpty()) {
            analyticsManager.onSongsSearchQueryChanged(query, shouldSearchInArtists, shouldSearchInTitles)
        }
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