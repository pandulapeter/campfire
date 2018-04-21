package com.pandulapeter.campfire.feature.home.collections

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel
import com.pandulapeter.campfire.util.swap

class CollectionsViewModel(
    context: Context,
    private val onDataLoaded: (languages: List<Language>) -> Unit
) : SongListViewModel(context) {

    var shouldShowSavedOnly = preferenceDatabase.shouldShowSavedOnly
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowSavedOnly = value
                updateAdapterItems()
            }
        }
    var disabledLanguageFilters = preferenceDatabase.disabledCollectionsLanguageFilters
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.disabledCollectionsLanguageFilters = value
                updateAdapterItems(true)
            }
        }
    var languages = mutableListOf<Language>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_COLLECTIONS
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
            placeholderText.set(R.string.collections_placeholder)
            buttonText.set(0)
        }
    }

    override fun onActionButtonClicked() = updateData()

    override fun Sequence<Song>.createViewModels() = filterSaved()
        .filterByLanguage()
        .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
        .toMutableList<SongListItemViewModel>()

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
    }

    private fun Sequence<Song>.filterSaved() = if (shouldShowSavedOnly) filter { songDetailRepository.isSongDownloaded(it.id) } else this

    private fun Sequence<Song>.filterByLanguage() = filter { !disabledLanguageFilters.contains(it.language ?: Language.Unknown.id) }
}