package com.pandulapeter.campfire.feature.main.collections

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.SearchControlsViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.mutableLiveDataOf
import com.pandulapeter.campfire.util.normalize
import com.pandulapeter.campfire.util.removePrefixes
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class CollectionsViewModel(
    context: Context,
    val preferenceDatabase: PreferenceDatabase,
    private val collectionRepository: CollectionRepository,
    private val analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), CollectionRepository.Subscriber {

    var isDetailScreenOpen = false
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    private val newText = context.getString(R.string.new_tag)
    val searchControlsViewModel = SearchControlsViewModel(
        preferenceDatabase,
        SearchControlsViewModel.Type.COLLECTIONS,
        interactionBlocker
    )
    var isTextInputVisible = false
    val state = mutableLiveDataOf(StateLayout.State.LOADING)
    val isLoading = mutableLiveDataOf(false)
    val isSearchToggleVisible = mutableLiveDataOf(false)
    val shouldShowUpdateErrorSnackbar = MutableLiveData<Boolean?>()
    val shouldScrollToTop = MutableLiveData<Boolean?>()
    val items = MutableLiveData<List<ItemViewModel>?>()
    val buttonText = mutableLiveDataOf(R.string.try_again)
    val shouldOpenSecondaryNavigationDrawer = MutableLiveData<Boolean?>()
    val isSwipeRefreshEnabled = mutableLiveDataOf(true)
    val shouldShowEraseButton = mutableLiveDataOf(false) { isSwipeRefreshEnabled.value = !it }
    val shouldEnableEraseButton = mutableLiveDataOf(false)
    val isFastScrollEnabled = mutableLiveDataOf(false)
    val changeEvent = MutableLiveData<Pair<Int, RecyclerAdapter.Payload>?>()
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
                trackSearchEvent()
                shouldEnableEraseButton.value = query.isNotEmpty()
            }
        }

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
    var shouldSearchInTitles = preferenceDatabase.shouldSearchInCollectionTitles
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
                trackSearchEvent()
            }
        }
    var shouldSearchInDescriptions = preferenceDatabase.shouldSearchInCollectionDescriptions
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
                trackSearchEvent()
            }
        }
    var languages = MutableLiveData<List<Language>?>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_COLLECTIONS
    }

    override fun subscribe() {
        collectionRepository.subscribe(this)
        isDetailScreenOpen = false
    }

    override fun unsubscribe() = collectionRepository.unsubscribe(this)

    override fun onCollectionsUpdated(data: List<Collection>) {
        collections = data.asSequence()
        updateAdapterItems()
        if (data.isNotEmpty() && languages.value != collectionRepository.languages) {
            languages.value = collectionRepository.languages
        }
    }

    override fun onCollectionsLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.value = isLoading
        if (collections.toList().isEmpty() && isLoading) {
            state.value = StateLayout.State.LOADING
        }
    }

    override fun onCollectionRepositoryUpdateError() {
        if (collections.toList().isEmpty()) {
            analyticsManager.onConnectionError(true, AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
            state.value = StateLayout.State.ERROR
        } else {
            analyticsManager.onConnectionError(false, AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
            shouldShowUpdateErrorSnackbar.value = true
        }
    }

    private fun onListUpdated(items: List<CollectionItemViewModel>) {
        state.value = if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL
        if (collections.toList().isNotEmpty()) {
            buttonText.value = if (isTextInputVisible) 0 else R.string.filters
            isFastScrollEnabled.value = sortingMode == SortingMode.TITLE
        }
    }

    fun onActionButtonClicked() {
        shouldOpenSecondaryNavigationDrawer.value = true
    }

    fun updateData() = collectionRepository.updateData()

    private fun Sequence<Collection>.createViewModels() = filterByQuery()
        .filterSaved()
        .filterExplicit()
        .filterByLanguage()
        .sort()
        .map { CollectionItemViewModel(it, newText) }
        .toList()

    private fun trackSearchEvent() {
        if (query.isNotEmpty()) {
            analyticsManager.onCollectionsSearchQueryChanged(query, shouldSearchInTitles, shouldSearchInDescriptions)
        }
    }

    fun onBookmarkClicked(position: Int, collection: Collection) {
        collectionRepository.toggleBookmarkedState(collection.id)
        analyticsManager.onCollectionBookmarkedStateChanged(
            collection.id,
            collection.isBookmarked == true,
            AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS
        )
        changeEvent.value = position to RecyclerAdapter.Payload.BookmarkedStateChanged(collection.isBookmarked ?: false)
        updateAdapterItems()
    }

    fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded()) {
            coroutine?.cancel()
            coroutine = launch(UI) {
                withContext(WORKER) { collections.createViewModels() }.let {
                    this@CollectionsViewModel.shouldScrollToTop.value = shouldScrollToTop
                    items.value = it
                    onListUpdated(it)
                }
                coroutine = null
            }
        }
    }

    private fun Sequence<Collection>.filterByQuery() = if (isTextInputVisible && query.isNotEmpty()) {
        query.trim().normalize().let { query ->
            filter {
                (it.getNormalizedTitle().contains(query, true) && shouldSearchInTitles) || (it.getNormalizedDescription().contains(query, true) && shouldSearchInDescriptions)
            }
        }
    } else this

    private fun Sequence<Collection>.filterSaved() = if (shouldShowSavedOnly) filter { it.isBookmarked ?: false } else this

    private fun Sequence<Collection>.filterExplicit() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    private fun Sequence<Collection>.filterByLanguage() = filter {
        var shouldFilter = false
        it.language?.forEach { language ->
            if (!disabledLanguageFilters.contains(language)) {
                shouldFilter = true
            }
        }
        shouldFilter
    }

    private fun Sequence<Collection>.sort() = when (sortingMode) {
        SortingMode.TITLE -> sortedByDescending { it.date }.sortedBy { it.getNormalizedTitle().removePrefixes() }
        SortingMode.UPLOAD_DATE -> sortedBy { it.getNormalizedTitle().removePrefixes() }.sortedByDescending { it.date }
        SortingMode.POPULARITY -> sortedByDescending { it.date }.sortedBy {
            it.getNormalizedTitle().removePrefixes()
        }.sortedByDescending { it.popularity }.sortedByDescending { it.isNew }
    }

    enum class SortingMode(val intValue: Int) {
        TITLE(0),
        UPLOAD_DATE(1),
        POPULARITY(2);

        companion object {
            fun fromIntValue(value: Int) = SortingMode.values().find { it.intValue == value } ?: TITLE
        }
    }
}