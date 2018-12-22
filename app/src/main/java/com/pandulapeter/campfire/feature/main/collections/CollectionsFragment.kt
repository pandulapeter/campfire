package com.pandulapeter.campfire.feature.main.collections

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.songs.SearchControlsViewModel
import com.pandulapeter.campfire.feature.main.songs.SongsFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.onTextChanged
import org.koin.androidx.viewmodel.ext.android.viewModel


class CollectionsFragment : CampfireFragment<FragmentCollectionsBinding, CollectionsViewModel>(R.layout.fragment_collections), TopLevelFragment {

    override val viewModel by viewModel<CollectionsViewModel>()
    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    override val topLevelBehavior by lazy {
        TopLevelBehavior(
            inflateToolbarTitle = { toolbarTextInputView },
            getCampfireActivity = { getCampfireActivity() })
    }
    private lateinit var linearLayoutManager: DisableScrollLinearLayoutManager
    private val drawableCloseToSearch by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getCampfireActivity()?.animatedDrawable(R.drawable.avd_close_to_search_24dp) else getCampfireActivity()?.drawable(R.drawable.ic_search_24dp)
    }
    private val drawableSearchToClose by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getCampfireActivity()?.animatedDrawable(R.drawable.avd_search_to_close_24dp) else getCampfireActivity()?.drawable(R.drawable.ic_close_24dp)
    }
    private val searchControlsViewModel = SearchControlsViewModel(true)
    private val searchControlsBinding by lazy {
        DataBindingUtil.inflate<ViewSearchControlsBinding>(LayoutInflater.from(getCampfireActivity()!!.toolbarContext), R.layout.view_search_controls, null, false).apply {
            viewModel = searchControlsViewModel
            executePendingBindings()
        }
    }
    private lateinit var toolbarTextInputView: ToolbarTextInputView
    private val searchToggle: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { toggleTextInputVisibility() }
    }
    private val eraseButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_eraser_24dp) { toolbarTextInputView.textInput.setText("") }.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0.5f
            isEnabled = false
        }
    }
    val recyclerAdapter = RecyclerAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                val index = recyclerAdapter.items.indexOfFirst { it is CollectionItemViewModel && it.collection.id == getCampfireActivity()?.lastCollectionId }
                if (index != RecyclerView.NO_POSITION) {
                    binding.recyclerView.findViewHolderForAdapterPosition(index)?.let {
                        val view = it.itemView
                        view.transitionName = "card-${getCampfireActivity()?.lastCollectionId}"
                        sharedElements[names[0]] = view
                        val image = view.findViewById<View>(R.id.image)
                        image.transitionName = "image-${getCampfireActivity()?.lastCollectionId}"
                        sharedElements[names[1]] = image
                    }
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        getCampfireActivity()?.let { activity ->
            toolbarTextInputView = ToolbarTextInputView(activity.toolbarContext, R.string.collections_search, true).apply {
                if (viewModel.isTextInputVisible) {
                    showTextInput()
                }
                textInput.setText(viewModel.query)
                visibilityChangeListener = { viewModel.isTextInputVisible = it }
                title.updateToolbarTitle(R.string.main_collections)
                textInput.onTextChanged { if (isTextInputVisible) viewModel.query = it }
            }
            topLevelBehavior.onViewCreated(savedInstanceState)
            postponeEnterTransition()
            viewModel.shouldOpenSecondaryNavigationDrawer.observeAndReset { getCampfireActivity()?.openSecondaryNavigationDrawer() }
            viewModel.languages.observe { languages ->
                if (languages != null) {
                    activity.updateAppBarView(searchControlsBinding.root)
                    activity.enableSecondaryNavigationDrawer(R.menu.collections)
                    initializeCompoundButton(R.id.sort_by_title) { viewModel.sortingMode == CollectionsViewModel.SortingMode.TITLE }
                    initializeCompoundButton(R.id.sort_by_date) { viewModel.sortingMode == CollectionsViewModel.SortingMode.UPLOAD_DATE }
                    initializeCompoundButton(R.id.sort_by_popularity) { viewModel.sortingMode == CollectionsViewModel.SortingMode.POPULARITY }
                    initializeCompoundButton(R.id.bookmarked_only) { viewModel.shouldShowSavedOnly }
                    initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                    activity.secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                        clear()
                        languages.forEachIndexed { index, language ->
                            add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                                setActionView(R.layout.widget_checkbox)
                                initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                            }
                        }
                    }
                    activity.updateToolbarButtons(
                        listOf(
                            eraseButton,
                            searchToggle,
                            activity.toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { activity.openSecondaryNavigationDrawer() }
                        ))
                }
            }
            analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
            viewModel.shouldScrollToTop.observeAndReset { recyclerAdapter.shouldScrollToTop = it }
            viewModel.items.observeNotNull { recyclerAdapter.items = it }
            viewModel.changeEvent.observeAndReset { recyclerAdapter.notifyItemChanged(it.first, it.second) }
            viewModel.shouldShowEraseButton.observe { eraseButton.animate().scaleX(if (it) 1f else 0f).scaleY(if (it) 1f else 0f).start() }
            viewModel.shouldEnableEraseButton.observe {
                eraseButton.animate().alpha(if (it) 1f else 0.5f).start()
                eraseButton.isEnabled = it
            }
            topLevelBehavior.defaultToolbar.updateToolbarTitle(R.string.main_collections)
            savedInstanceState?.let {
                searchControlsViewModel.isVisible.set(savedInstanceState.isTextInputVisible)
                viewModel.buttonText.value = it.buttonText
                if (it.isTextInputVisible) {
                    searchToggle.setImageDrawable(activity.drawable(R.drawable.ic_close_24dp))
                    toolbarTextInputView.textInput.run {
                        setText(savedInstanceState.searchQuery)
                        setSelection(text.length)
                        viewModel.query = text.toString()
                    }
                    toolbarTextInputView.showTextInput()
                }
                viewModel.shouldShowEraseButton.value = savedInstanceState.isEraseButtonVisible
                viewModel.shouldEnableEraseButton.value = savedInstanceState.isEraseButtonEnabled
            }
            toolbarTextInputView.textInput.requestFocus()
            viewModel.shouldShowUpdateErrorSnackbar.observeAndReset {
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
            viewModel.isSearchToggleVisible.observeAfterDelay {
                searchToggle.setImageDrawable((if (it) drawableSearchToClose else drawableCloseToSearch).apply { (this as? AnimatedVectorDrawableCompat)?.start() })
                activity.transitionMode = true
                binding.root.post {
                    if (isAdded) {
                        searchControlsViewModel.isVisible.set(it)
                    }
                }
            }
            recyclerAdapter.apply {
                collectionClickListener = { collection, clickedView, image ->
                    if (linearLayoutManager.isScrollEnabled && !isUiBlocked) {
                        if (items.size > 1) {
                            linearLayoutManager.isScrollEnabled = false
                            viewModel.isDetailScreenOpen = true
                        }
                        isUiBlocked = true
                        if (toolbarTextInputView.isTextInputVisible && viewModel.query.trim().isEmpty()) {
                            toggleTextInputVisibility()
                        }
                        activity.openCollectionDetailsScreen(collection, clickedView, image, items.size > 1)
                    }
                }
                collectionBookmarkClickListener = { collection, position ->
                    if (linearLayoutManager.isScrollEnabled && !isUiBlocked) {
                        viewModel.onBookmarkClicked(position, collection)
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
            linearLayoutManager = DisableScrollLinearLayoutManager(activity)
            binding.recyclerView.apply {
                layoutManager = linearLayoutManager
                setHasFixedSize(true)
                adapter = recyclerAdapter
                itemAnimator = object : DefaultItemAnimator() {
                    init {
                        supportsChangeAnimations = false
                    }
                }
                addOnLayoutChangeListener(
                    object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                            removeOnLayoutChangeListener(this)
                            if (reenterTransition != null) {
                                val index = recyclerAdapter.items.indexOfFirst { it is CollectionItemViewModel && it.collection.id == getCampfireActivity()?.lastCollectionId }
                                if (index != RecyclerView.NO_POSITION) {
                                    val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                                    if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                        linearLayoutManager.isScrollEnabled = true
                                        post { if (isAdded) scrollToPosition(index) }
                                    }
                                }
                            }
                        }
                    })
            }
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
            activity.showPlayStoreRatingDialogIfNeeded()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.isTextInputVisible = toolbarTextInputView.isTextInputVisible
        outState.searchQuery = viewModel.query
        outState.isEraseButtonVisible = viewModel.shouldShowEraseButton.value == true
        outState.isEraseButtonEnabled = viewModel.shouldEnableEraseButton.value == true
        viewModel.buttonText.value?.let { outState.buttonText = it }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun onBackPressed() = if (toolbarTextInputView.isTextInputVisible) consume { toggleTextInputVisibility() } else super.onBackPressed()

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
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.value.orEmpty().find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    private fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !viewModel.query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                viewModel.isSearchToggleVisible.value = toolbarTextInputView.isTextInputVisible
                if (shouldScrollToTop) {
                    viewModel.updateAdapterItems(!isTextInputVisible)
                }
                viewModel.buttonText.value = if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters
            }
            viewModel.shouldShowEraseButton.value = isTextInputVisible
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
        getCampfireActivity()?.apply {
            (secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.TITLE)
            (secondaryNavigationMenu[R.id.sort_by_date].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.UPLOAD_DATE)
            (secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.POPULARITY)
        }
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)

    companion object {
        private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
        private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
        private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
        private var Bundle.isEraseButtonVisible by BundleArgumentDelegate.Boolean("isEraseButtonVisible")
        private var Bundle.isEraseButtonEnabled by BundleArgumentDelegate.Boolean("isEraseButtonEnabled")

        fun newInstance() = CollectionsFragment()
    }
}