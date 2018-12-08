package com.pandulapeter.campfire.feature.main.collections

import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class CollectionsViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    private val collectionRepository: CollectionRepository,
    private val analyticsManager: AnalyticsManager,
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit,
    private val newText: String
) : OldCampfireViewModel(), CollectionRepository.Subscriber {

    var isDetailScreenOpen = false
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    var isTextInputVisible = false
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val isLoading = ObservableBoolean()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val buttonText = ObservableInt(R.string.try_again)
    val adapter = RecyclerAdapter()
    val isSwipeRefreshEnabled = ObservableBoolean(true)
    val shouldShowEraseButton = ObservableBoolean().apply {
        onPropertyChanged {
            isSwipeRefreshEnabled.set(!it)
        }
    }
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
            field = value
            updateAdapterItems(true)
            trackSearchEvent()
        }
    var shouldSearchInDescriptions = preferenceDatabase.shouldSearchInCollectionDescriptions
        set(value) {
            field = value
            updateAdapterItems(true)
            trackSearchEvent()
        }
    var languages = mutableListOf<Language>()

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
            analyticsManager.onConnectionError(true, AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
            state.set(StateLayout.State.ERROR)
        } else {
            analyticsManager.onConnectionError(false, AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
            shouldShowUpdateErrorSnackbar.set(true)
        }
    }

    private fun onListUpdated(items: List<CollectionItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        if (collections.toList().isNotEmpty()) {
            buttonText.set(if (isTextInputVisible) 0 else R.string.filters)
        }
    }

    fun onActionButtonClicked() {
        openSecondaryNavigationDrawer()
    }

    fun updateData() = collectionRepository.updateData()

    private fun Sequence<Collection>.createViewModels() = filterByQuery()
        .filterSaved()
        .filterExplicit()
        .filterByLanguage()
        .sort()
        .map { CollectionItemViewModel(it, newText) }
        .toList()

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
    }

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
        adapter.notifyItemChanged(position, RecyclerAdapter.Payload.BookmarkedStateChanged(collection.isBookmarked ?: false))
        updateAdapterItems()
    }

    fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded()) {
            coroutine?.cancel()
            coroutine = GlobalScope.launch(UI) {
                withContext(WORKER) { collections.createViewModels() }.let {
                    adapter.shouldScrollToTop = shouldScrollToTop
                    adapter.items = it
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
        it.language?.forEach {
            if (!disabledLanguageFilters.contains(it)) {
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