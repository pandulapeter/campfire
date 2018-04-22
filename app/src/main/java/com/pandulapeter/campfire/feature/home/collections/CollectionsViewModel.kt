package com.pandulapeter.campfire.feature.home.collections

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

class CollectionsViewModel(private val onDataLoaded: (languages: List<Language>) -> Unit) : CampfireViewModel(), CollectionRepository.Subscriber {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val collectionRepository by inject<CollectionRepository>()
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val isLoading = ObservableBoolean()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val placeholderText = ObservableInt(R.string.library_initializing_error)
    val buttonText = ObservableInt(R.string.try_again)
    val buttonIcon = ObservableInt()
    val adapter = CollectionListAdapter()
    var sortingMode = SortingMode.fromIntValue(preferenceDatabase.collectionsSortingMode)
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.collectionsSortingMode = value.intValue
                updateAdapterItems(true)
            }
        }
    var shouldShowSavedOnly = preferenceDatabase.shouldShowSavedOnly
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowSavedOnly = value
                updateAdapterItems()
            }
        }
    var shouldShowExplicit = preferenceDatabase.shouldShowExplicitCollections
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowExplicitCollections = value
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

    override fun subscribe() = collectionRepository.subscribe(this)

    override fun unsubscribe() = collectionRepository.unsubscribe(this)

    override fun onCollectionsUpdated(data: List<Collection>) {
        collections = data.asSequence()
        updateAdapterItems(true)
        if (data.isNotEmpty()) {
            languages.swap(collectionRepository.languages)
            onDataLoaded(languages)
        }
    }

    override fun onCollectionsLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.set(isLoading)
        if (collections.toList().isEmpty() && isLoading) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onCollectionRepositoryUpdateError() {
        if (collections.toList().isEmpty()) {
            state.set(StateLayout.State.ERROR)
        } else {
            shouldShowUpdateErrorSnackbar.set(true)
        }
    }


    private fun onListUpdated(items: List<CollectionListItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        if (collections.toList().isNotEmpty()) {
            placeholderText.set(R.string.collections_placeholder)
            buttonText.set(0)
        }
    }

    fun onActionButtonClicked() = updateData()

    fun updateData() = collectionRepository.updateData()

    private fun Sequence<Collection>.createViewModels() = filterSaved()
        .filterByLanguage()
        .map { CollectionListItemViewModel.CollectionViewModel(it) }
        .toMutableList<CollectionListItemViewModel>()

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
    }

    private fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded()) {
            coroutine?.cancel()
            coroutine = async(UI) {
                async(CommonPool) { collections.createViewModels() }.await().let {
                    adapter.shouldScrollToTop = shouldScrollToTop
                    adapter.items = it
                    onListUpdated(it)
                }
                coroutine = null
            }
        }
    }

    private fun Sequence<Collection>.filterSaved() = if (shouldShowSavedOnly) filter { it.isSaved ?: false } else this

    private fun Sequence<Collection>.filterByLanguage() = filter { !disabledLanguageFilters.contains(it.language ?: Language.Unknown.id) }

    enum class SortingMode(val intValue: Int) {
        TITLE(0),
        UPLOAD_DATE(1),
        POPULARITY(2);

        companion object {
            fun fromIntValue(value: Int) = SortingMode.values().find { it.intValue == value } ?: TITLE
        }
    }
}