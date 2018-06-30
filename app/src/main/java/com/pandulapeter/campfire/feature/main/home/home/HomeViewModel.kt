package com.pandulapeter.campfire.feature.main.home.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

class HomeViewModel(
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit
) : CampfireViewModel(), CollectionRepository.Subscriber, SongRepository.Subscriber {

    private val analyticsManager by inject<AnalyticsManager>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val collectionRepository by inject<CollectionRepository>()
    private val songRepository by inject<SongRepository>()
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    private var songs = sequenceOf<Song>()
    val adapter = HomeAdapter()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val isLoading = ObservableBoolean()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val placeholderText = ObservableInt(R.string.home_initializing_error)
    val buttonText = ObservableInt(R.string.try_again)
    val buttonIcon = ObservableInt()
    private var isFirstLoadingDone = false
    var shouldShowExplicit = preferenceDatabase.shouldShowExplicit
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowExplicit = value
                updateAdapterItems()
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

    override fun subscribe() {
        collectionRepository.subscribe(this)
        songRepository.subscribe(this)
    }

    override fun unsubscribe() {
        songRepository.unsubscribe(this)
        collectionRepository.unsubscribe(this)
    }

    override fun onCollectionsUpdated(data: List<Collection>) {
        collections = data.asSequence()
        updateAdapterItems(true)
    }

    override fun onCollectionsLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.set(isLoading)
        if (collections.toList().isEmpty() && isLoading) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onCollectionRepositoryUpdateError() = onError()

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        songs = data.asSequence()
        updateAdapterItems(true)
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.set(isLoading)
        if (songs.toList().isEmpty() && isLoading) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onSongRepositoryUpdateError() = onError()

    private fun onError() {
        if (collections.toList().isEmpty() || songs.toList().isEmpty()) {
            analyticsManager.onConnectionError(true, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
            state.set(StateLayout.State.ERROR)
        } else {
            analyticsManager.onConnectionError(false, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
            shouldShowUpdateErrorSnackbar.set(true)
        }
    }

    private fun onListUpdated(items: List<HomeItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        if (collections.toList().isNotEmpty()) {
            placeholderText.set(R.string.home_placeholder)
            buttonText.set(R.string.filters)
            buttonIcon.set(R.drawable.ic_filter_and_sort_24dp)
        }
    }

    fun onActionButtonClicked() {
        if (buttonIcon.get() == 0) {
            updateData()
        } else {
            openSecondaryNavigationDrawer()
        }
    }

    fun updateData() {
        collectionRepository.updateData()
        songRepository.updateData()
    }

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
    }

    private fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded() && songRepository.isCacheLoaded() && collections.toList().isNotEmpty() && songs.toList().isNotEmpty()) {
            if (!isFirstLoadingDone) {
                languages.swap(collectionRepository.languages.union(songRepository.languages).toList())
                onDataLoaded(languages)
                isFirstLoadingDone = true
            }
            coroutine?.cancel()
            coroutine = launch(UI) {
                withContext(CommonPool) { createViewModels() }.let {
                    adapter.shouldScrollToTop = shouldScrollToTop
                    adapter.items = it
                    onListUpdated(it)
                }
                coroutine = null
            }
        }
    }

    private fun createViewModels() = listOf<HomeItemViewModel>()
//        filterSaved()
//        .filterExplicit()
//        .filterByLanguage()
//        .sort()
//        .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
//        .toList<CollectionListItemViewModel>()

    private fun Sequence<Collection>.filterExplicitCollections() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    private fun Sequence<Song>.filterExplicitSongs() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this
}