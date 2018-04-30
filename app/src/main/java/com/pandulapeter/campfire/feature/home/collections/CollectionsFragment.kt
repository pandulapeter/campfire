package com.pandulapeter.campfire.feature.home.collections

import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v4.app.SharedElementCallback
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.CompoundButton
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentCollectionsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.*


class CollectionsFragment : TopLevelFragment<FragmentCollectionsBinding, CollectionsViewModel>(R.layout.fragment_collections) {

    private var Bundle.placeholderText by BundleArgumentDelegate.Int("placeholderText")
    private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
    private var Bundle.buttonIcon by BundleArgumentDelegate.Int("buttonIcon")
    override val viewModel: CollectionsViewModel by lazy {
        CollectionsViewModel(
            onDataLoaded = { languages ->
                mainActivity.enableSecondaryNavigationDrawer(R.menu.collections)
                initializeCompoundButton(R.id.sort_by_title) { viewModel.sortingMode == CollectionsViewModel.SortingMode.TITLE }
                initializeCompoundButton(R.id.sort_by_date) { viewModel.sortingMode == CollectionsViewModel.SortingMode.UPLOAD_DATE }
                initializeCompoundButton(R.id.sort_by_popularity) { viewModel.sortingMode == CollectionsViewModel.SortingMode.POPULARITY }
                initializeCompoundButton(R.id.bookmarked_only) { viewModel.shouldShowSavedOnly }
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                mainActivity.secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
                mainActivity.updateToolbarButtons(listOf(
                    mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { mainActivity.openSecondaryNavigationDrawer() }
                ))
            },
            openSecondaryNavigationDrawer = { mainActivity.openSecondaryNavigationDrawer() },
            onUpdateScrollState = { binding.recyclerView.post { updateScrollState() } })
    }
    override val canScrollToolbar get() = binding.recyclerView.canScroll()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                val index = viewModel.adapter.items.indexOfFirst { it is CollectionListItemViewModel.CollectionViewModel && it.collection.id == mainActivity.lastCollectionId }
                if (index != RecyclerView.NO_POSITION) {
                    binding.recyclerView.findViewHolderForAdapterPosition(index)?.let {
                        val view = it.itemView
                        view.transitionName = getString(R.string.campfire)
                        sharedElements[names[0]] = view
                        val image = view.findViewById<View>(R.id.image)
                        image.transitionName = getString(R.string.home_collections)
                        sharedElements[names[1]] = image
                    }
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            viewModel.placeholderText.set(it.placeholderText)
            viewModel.buttonText.set(it.buttonText)
            viewModel.buttonIcon.set(it.buttonIcon)
        }
        defaultToolbar.updateToolbarTitle(R.string.home_collections)
        viewModel.shouldShowUpdateErrorSnackbar.onEventTriggered(this) {
            showSnackbar(
                message = R.string.collections_update_error,
                action = { viewModel.updateData() })
        }
        viewModel.isLoading.onPropertyChanged(this) {
            if (viewModel.state.get() == StateLayout.State.NORMAL) {
                binding.swipeRefreshLayout.isRefreshing = it
            }
        }
        viewModel.adapter.apply {
            itemClickListener = { position, clickedView, image ->
                (items[position] as? CollectionListItemViewModel.CollectionViewModel)?.collection?.let {
                    viewModel.collectionRepository.onCollectionOpened(it.id)
                    analyticsManager.onCollectionOpened(it.id)
                    mainActivity.openCollectionDetailsScreen(it, clickedView, image, items.size > 1)
                }
            }
        }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener { viewModel.updateData() }
            setColorSchemeColors(context.color(R.color.accent))
        }
        binding.recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
        }
        binding.recyclerView.addOnLayoutChangeListener(
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    binding.recyclerView.removeOnLayoutChangeListener(this)
                    if (reenterTransition != null) {
                        val index =
                            viewModel.adapter.items.indexOfFirst { it is CollectionListItemViewModel.CollectionViewModel && it.collection.id == mainActivity.lastCollectionId }
                        if (index != RecyclerView.NO_POSITION) {
                            val linearLayoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
                            val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                            if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
//TODO                                linearLayoutManager.isScrollEnabled = true
                                binding.recyclerView.run { post { scrollToPosition(index) } }
                            }
                        }
                    }
                }
            })
        (view.parent as? ViewGroup)?.run {
            viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver?.removeOnPreDrawListener(this)
                    post { startPostponedEnterTransition() }
                    return true
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.placeholderText = viewModel.placeholderText.get()
        outState.buttonText = viewModel.buttonText.get()
        outState.buttonIcon = viewModel.buttonIcon.get()
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.sort_by_title -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.TITLE) { sortingMode = it }
            R.id.sort_by_date -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.UPLOAD_DATE) { sortingMode = it }
            R.id.sort_by_popularity -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.POPULARITY) { sortingMode = it }
            R.id.bookmarked_only -> consumeAndUpdateBoolean(menuItem, { shouldShowSavedOnly = it }, { shouldShowSavedOnly })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, { shouldShowExplicit = it }, { shouldShowExplicit })
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }

    private inline fun consumeAndUpdateSortingMode(sortingMode: CollectionsViewModel.SortingMode, crossinline setValue: (CollectionsViewModel.SortingMode) -> Unit) = consume {
        setValue(sortingMode)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.TITLE)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_date].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.UPLOAD_DATE)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.POPULARITY)
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)
}