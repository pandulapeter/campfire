package com.pandulapeter.campfire.feature.main.collections

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
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

class CollectionsViewModel(
    val toolbarTextInputView: ToolbarTextInputView,
    private val updateSearchToggleDrawable: (Boolean) -> Unit,
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit,
    private val newText: String
) : CampfireViewModel(), CollectionRepository.Subscriber {

    var isDetailScreenOpen = false
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val collectionRepository by inject<CollectionRepository>()
    private val analyticsManager by inject<AnalyticsManager>()
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val isLoading = ObservableBoolean()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val placeholderText = ObservableInt(R.string.collections_initializing_error)
    val buttonText = ObservableInt(R.string.try_again)
    val buttonIcon = ObservableInt()
    val adapter = CollectionListAdapter()
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
    var languages = mutableListOf<Language>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_COLLECTIONS
        toolbarTextInputView.apply {
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
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

    private fun onListUpdated(items: List<CollectionListItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        if (collections.toList().isNotEmpty()) {
            placeholderText.set(R.string.collections_placeholder)
            buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters)
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

    fun updateData() = collectionRepository.updateData()

    private fun Sequence<Collection>.createViewModels() = filterCollectionsByQuery()
        .filterSaved()
        .filterExplicit()
        .filterByLanguage()
        .sort()
        .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
        .toList<CollectionListItemViewModel>()

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
                buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters)
            }
            shouldShowEraseButton.set(isTextInputVisible)
        }
    }

    private fun trackSearchEvent() {
        if (query.isNotEmpty()) {
            analyticsManager.onCollectionsSearchQueryChanged(query)
        }
    }

    fun onBookmarkClicked(position: Int, collection: Collection) {
        collectionRepository.toggleBookmarkedState(collection.id)
        analyticsManager.onCollectionBookmarkedStateChanged(
            collection.id,
            collection.isBookmarked == true,
            AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS
        )
        adapter.notifyItemChanged(position, CollectionListAdapter.Payload.BookmarkedStateChanged(collection.isBookmarked ?: false))
        updateAdapterItems()
    }

    private fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded()) {
            coroutine?.cancel()
            coroutine = launch(UI) {
                withContext(CommonPool) { collections.createViewModels() }.let {
                    adapter.shouldScrollToTop = shouldScrollToTop
                    adapter.items = it
                    onListUpdated(it)
                }
                coroutine = null
            }
        }
    }

    private fun Sequence<Collection>.filterCollectionsByQuery() = if (toolbarTextInputView.isTextInputVisible && query.isNotEmpty()) {
        query.trim().normalize().let { query ->
            filter {
                it.getNormalizedTitle().contains(query, true) || it.getNormalizedDescription().contains(query, true)
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
        SortingMode.TITLE -> sortedBy { it.date }.sortedBy { it.getNormalizedTitle().removePrefixes() }
        SortingMode.UPLOAD_DATE -> sortedBy { it.getNormalizedTitle().removePrefixes() }.sortedBy { it.date }
        SortingMode.POPULARITY -> sortedBy { it.date }.sortedBy { it.getNormalizedTitle().removePrefixes() }.sortedByDescending { it.popularity }.sortedByDescending { it.isNew }
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