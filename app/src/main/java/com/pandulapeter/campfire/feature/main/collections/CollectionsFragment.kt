package com.pandulapeter.campfire.feature.main.collections

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.CompoundButton
import androidx.annotation.IdRes
import androidx.core.app.SharedElementCallback
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentCollectionsBinding
import com.pandulapeter.campfire.databinding.ViewSearchControlsBinding
import com.pandulapeter.campfire.feature.main.songs.SearchControlsViewModel
import com.pandulapeter.campfire.feature.main.songs.SongsFragment
import com.pandulapeter.campfire.feature.shared.deprecated.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.*


class CollectionsFragment : TopLevelFragment<FragmentCollectionsBinding, CollectionsViewModel>(R.layout.fragment_collections) {

    private var Bundle.placeholderText by BundleArgumentDelegate.Int("placeholderText")
    private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
    private var Bundle.buttonIcon by BundleArgumentDelegate.Int("buttonIcon")
    private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
    private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
    private var Bundle.isEraseButtonVisible by BundleArgumentDelegate.Boolean("isEraseButtonVisible")
    private var Bundle.isEraseButtonEnabled by BundleArgumentDelegate.Boolean("isEraseButtonEnabled")
    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    private lateinit var linearLayoutManager: DisableScrollLinearLayoutManager
    private val searchToggle: ToolbarButton by lazy {
        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) {
            viewModel.toggleTextInputVisibility()
        }
    }
    private val drawableCloseToSearch by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getCampfireActivity().animatedDrawable(R.drawable.avd_close_to_search_24dp) else getCampfireActivity().drawable(R.drawable.ic_search_24dp)
    }
    private val drawableSearchToClose by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getCampfireActivity().animatedDrawable(R.drawable.avd_search_to_close_24dp) else getCampfireActivity().drawable(R.drawable.ic_close_24dp)
    }
    private val searchControlsViewModel = SearchControlsViewModel(true)
    private val searchControlsBinding by lazy {
        DataBindingUtil.inflate<ViewSearchControlsBinding>(LayoutInflater.from(getCampfireActivity().toolbarContext), R.layout.view_search_controls, null, false).apply {
            viewModel = searchControlsViewModel
            executePendingBindings()
        }
    }
    private val eraseButton: ToolbarButton by lazy {
        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_eraser_24dp) {
            viewModel.toolbarTextInputView.textInput.setText("")
        }.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0.5f
            isEnabled = false
        }
    }
    override val viewModel: CollectionsViewModel by lazy {
        CollectionsViewModel(
            toolbarTextInputView = ToolbarTextInputView(
                getCampfireActivity().toolbarContext,
                R.string.songs_search,
                true
            ).apply { title.updateToolbarTitle(R.string.main_collections) },
            updateSearchToggleDrawable = {
                searchToggle.setImageDrawable((if (it) drawableSearchToClose else drawableCloseToSearch).apply { (this as? AnimatedVectorDrawableCompat)?.start() })
                getCampfireActivity().transitionMode = true
                binding.root.post {
                    if (isAdded) {
                        searchControlsViewModel.isVisible.set(it)
                    }
                }
            },
            onDataLoaded = { languages ->
                getCampfireActivity().updateAppBarView(searchControlsBinding.root)
                getCampfireActivity().enableSecondaryNavigationDrawer(R.menu.collections)
                initializeCompoundButton(R.id.sort_by_title) { viewModel.sortingMode == CollectionsViewModel.SortingMode.TITLE }
                initializeCompoundButton(R.id.sort_by_date) { viewModel.sortingMode == CollectionsViewModel.SortingMode.UPLOAD_DATE }
                initializeCompoundButton(R.id.sort_by_popularity) { viewModel.sortingMode == CollectionsViewModel.SortingMode.POPULARITY }
                initializeCompoundButton(R.id.bookmarked_only) { viewModel.shouldShowSavedOnly }
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                getCampfireActivity().secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
                getCampfireActivity().updateToolbarButtons(
                    listOf(
                        eraseButton,
                        searchToggle,
                        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { getCampfireActivity().openSecondaryNavigationDrawer() }
                    ))
            },
            openSecondaryNavigationDrawer = { getCampfireActivity().openSecondaryNavigationDrawer() },
            newText = getString(R.string.new_tag)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                val index =
                    viewModel.adapter.items.indexOfFirst { it is CollectionListItemViewModel.CollectionViewModel && it.collection.id == getCampfireActivity().lastCollectionId }
                if (index != RecyclerView.NO_POSITION) {
                    binding.recyclerView.findViewHolderForAdapterPosition(index)?.let {
                        val view = it.itemView
                        view.transitionName = "card-${getCampfireActivity().lastCollectionId}"
                        sharedElements[names[0]] = view
                        val image = view.findViewById<View>(R.id.image)
                        image.transitionName = "image-${getCampfireActivity().lastCollectionId}"
                        sharedElements[names[1]] = image
                    }
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
        viewModel.shouldShowEraseButton.onPropertyChanged { eraseButton.animate().scaleX(if (it) 1f else 0f).scaleY(if (it) 1f else 0f).start() }
        viewModel.shouldEnableEraseButton.onPropertyChanged {
            eraseButton.animate().alpha(if (it) 1f else 0.5f).start()
            eraseButton.isEnabled = it
        }
        viewModel.toolbarTextInputView.textInput.requestFocus()
        savedInstanceState?.let {
            searchControlsViewModel.isVisible.set(savedInstanceState.isTextInputVisible)
            viewModel.placeholderText.set(it.placeholderText)
            viewModel.buttonText.set(it.buttonText)
            viewModel.buttonIcon.set(it.buttonIcon)
            if (it.isTextInputVisible) {
                searchToggle.setImageDrawable(getCampfireActivity().drawable(R.drawable.ic_close_24dp))
                viewModel.toolbarTextInputView.textInput.run {
                    setText(savedInstanceState.searchQuery)
                    setSelection(text.length)
                    viewModel.query = text.toString()
                }
                viewModel.toolbarTextInputView.showTextInput()
            }
            viewModel.shouldShowEraseButton.set(savedInstanceState.isEraseButtonVisible)
            viewModel.shouldEnableEraseButton.set(savedInstanceState.isEraseButtonEnabled)
        }
        defaultToolbar.updateToolbarTitle(R.string.main_collections)
        viewModel.shouldShowUpdateErrorSnackbar.onEventTriggered(this) {
            showSnackbar(
                message = R.string.collections_update_error,
                action = { viewModel.updateData() })
        }
        searchControlsViewModel.searchInTitles.onPropertyChanged(this) {
            binding.root.postDelayed(
                { if (isAdded) viewModel.shouldSearchInTitles = it },
                SongsFragment.COMPOUND_BUTTON_LONG_TRANSITION_DELAY
            )
        }
        searchControlsViewModel.searchInArtists.onPropertyChanged(this) {
            binding.root.postDelayed(
                { if (isAdded) viewModel.shouldSearchInDescriptions = it },
                SongsFragment.COMPOUND_BUTTON_LONG_TRANSITION_DELAY
            )
        }
        viewModel.adapter.apply {
            itemClickListener = { position, clickedView, image ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    (items[position] as? CollectionListItemViewModel.CollectionViewModel)?.collection?.let {
                        if (items.size > 1) {
                            linearLayoutManager.isScrollEnabled = false
                            viewModel.isDetailScreenOpen = true
                        }
                        getCampfireActivity().isUiBlocked = true
                        if (viewModel.toolbarTextInputView.isTextInputVisible && viewModel.query.trim().isEmpty()) {
                            viewModel.toggleTextInputVisibility()
                        }
                        viewModel.collectionRepository.onCollectionOpened(it.id)
                        getCampfireActivity().openCollectionDetailsScreen(it, clickedView, image, items.size > 1)
                    }
                }
            }
            bookmarkActionClickListener = { position ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    viewModel.adapter.items[position].let {
                        if (it is CollectionListItemViewModel.CollectionViewModel) {
                            viewModel.onBookmarkClicked(position, it.collection)
                        }
                    }
                }
            }
        }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener {
                analyticsManager.onSwipeToRefreshUsed(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
                viewModel.updateData()
            }
            setColorSchemeColors(context.color(R.color.accent))
        }
        linearLayoutManager = DisableScrollLinearLayoutManager(getCampfireActivity())
        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.itemAnimator = object : DefaultItemAnimator() {
            init {
                supportsChangeAnimations = false
            }
        }
        binding.recyclerView.addOnLayoutChangeListener(
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    binding.recyclerView.removeOnLayoutChangeListener(this)
                    if (reenterTransition != null) {
                        val index =
                            viewModel.adapter.items.indexOfFirst { it is CollectionListItemViewModel.CollectionViewModel && it.collection.id == getCampfireActivity().lastCollectionId }
                        if (index != RecyclerView.NO_POSITION) {
                            val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                            if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                linearLayoutManager.isScrollEnabled = true
                                binding.recyclerView.run { post { if (isAdded) scrollToPosition(index) } }
                            }
                        }
                    }
                }
            })
        (view.parent as? ViewGroup)?.run {
            viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver?.removeOnPreDrawListener(this)
                    startPostponedEnterTransition()
                    return true
                }
            })
            requestLayout()
        }
        getCampfireActivity().showPlayStoreRatingDialogIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.isTextInputVisible = viewModel.toolbarTextInputView.isTextInputVisible
        outState.searchQuery = viewModel.query
        outState.isEraseButtonVisible = viewModel.shouldShowEraseButton.get()
        outState.isEraseButtonEnabled = viewModel.shouldEnableEraseButton.get()
        outState.placeholderText = viewModel.placeholderText.get()
        outState.buttonText = viewModel.buttonText.get()
        outState.buttonIcon = viewModel.buttonIcon.get()
    }

    override fun inflateToolbarTitle(context: Context) = viewModel.toolbarTextInputView

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun onBackPressed() = if (viewModel.toolbarTextInputView.isTextInputVisible) {
        viewModel.toggleTextInputVisibility()
        true
    } else super.onBackPressed()

    override fun updateUI() {
        super.updateUI()
        linearLayoutManager.isScrollEnabled = true
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.sort_by_title -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.TITLE) {
                analyticsManager.onCollectionSortingModeUpdated(AnalyticsManager.PARAM_VALUE_BY_TITLE)
                sortingMode = it
            }
            R.id.sort_by_date -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.UPLOAD_DATE) {
                analyticsManager.onCollectionSortingModeUpdated(AnalyticsManager.PARAM_VALUE_BY_DATE)
                sortingMode = it
            }
            R.id.sort_by_popularity -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.POPULARITY) {
                analyticsManager.onCollectionSortingModeUpdated(AnalyticsManager.PARAM_VALUE_BY_POPULARITY)
                sortingMode = it
            }
            R.id.bookmarked_only -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onCollectionFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_BOOKMARKED_ONLY, it)
                shouldShowSavedOnly = it
            }, { shouldShowSavedOnly })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onCollectionFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_EXPLICIT, it)
                shouldShowExplicit = it
            }, { shouldShowExplicit })
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            analyticsManager.onCollectionFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_LANGUAGE + languageId, contains(languageId))
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }

    private inline fun consumeAndUpdateSortingMode(sortingMode: CollectionsViewModel.SortingMode, crossinline setValue: (CollectionsViewModel.SortingMode) -> Unit) = consume {
        setValue(sortingMode)
        (getCampfireActivity().secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.TITLE)
        (getCampfireActivity().secondaryNavigationMenu[R.id.sort_by_date].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.UPLOAD_DATE)
        (getCampfireActivity().secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.POPULARITY)
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)
}