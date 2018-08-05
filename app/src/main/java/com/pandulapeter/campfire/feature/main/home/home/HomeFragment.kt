package com.pandulapeter.campfire.feature.main.home.home

import android.os.Bundle
import android.support.v4.app.SharedElementCallback
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.RecyclerView
import android.transition.Transition
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.databinding.FragmentHomeBinding
import com.pandulapeter.campfire.feature.main.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.main.shared.baseSongList.SongListItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.*

class HomeFragment : CampfireFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    companion object {
        private var Bundle.shouldAnimate by BundleArgumentDelegate.Boolean("shouldAnimate")

        fun newInstance(shouldAnimate: Boolean) = HomeFragment().withArguments {
            it.shouldAnimate = shouldAnimate
        }
    }

    private var Bundle.placeholderText by BundleArgumentDelegate.Int("placeholderText")
    private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
    private var Bundle.buttonIcon by BundleArgumentDelegate.Int("buttonIcon")
    private var Bundle.wasLastTransitionForACollection by BundleArgumentDelegate.Boolean("wasLastTransitionForACollection")
    private var Bundle.randomCollections by BundleArgumentDelegate.ParcelableArrayList<Collection>("randomCollections")
    private var Bundle.randomSongs by BundleArgumentDelegate.ParcelableArrayList<Song>("randomSongs")
    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    override val viewModel: HomeViewModel by lazy {
        HomeViewModel(
            onDataLoaded = { languages ->
                getCampfireActivity().enableSecondaryNavigationDrawer(R.menu.home)
                initializeCompoundButton(R.id.new_collections) { viewModel.shouldShowNewCollections }
                initializeCompoundButton(R.id.new_songs) { viewModel.shouldShowNewSongs }
                initializeCompoundButton(R.id.random_collections) { viewModel.shouldShowRandomCollections }
                initializeCompoundButton(R.id.random_songs) { viewModel.shouldShowRandomSongs }
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
                        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { getCampfireActivity().openSecondaryNavigationDrawer() }
                    ))
            },
            openSecondaryNavigationDrawer = { getCampfireActivity().openSecondaryNavigationDrawer() },
            context = getCampfireActivity()
        )
    }
    private lateinit var linearLayoutManager: DisableScrollLinearLayoutManager
    private var wasLastTransitionForACollection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragment?.setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                var index =
                    viewModel.adapter.items.indexOfFirst { it is CollectionListItemViewModel.CollectionViewModel && it.collection.id == getCampfireActivity().lastCollectionId }
                if (wasLastTransitionForACollection && index != RecyclerView.NO_POSITION) {
                    binding.recyclerView.findViewHolderForAdapterPosition(index)?.let {
                        val view = it.itemView
                        view.transitionName = "card-${getCampfireActivity().lastCollectionId}"
                        sharedElements[names[0]] = view
                        val image = view.findViewById<View>(R.id.image)
                        image.transitionName = "image-${getCampfireActivity().lastCollectionId}"
                        sharedElements[names[1]] = image
                    }
                } else {
                    index = viewModel.adapter.items.indexOfFirst { it is SongListItemViewModel.SongViewModel && it.song.id == getCampfireActivity().lastSongId }
                    if (index != RecyclerView.NO_POSITION) {
                        (binding.recyclerView.findViewHolderForAdapterPosition(index)
                                ?: binding.recyclerView.findViewHolderForAdapterPosition(linearLayoutManager.findLastVisibleItemPosition()))?.let {
                            sharedElements[names[0]] = it.itemView
                        }
                    }
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (arguments?.shouldAnimate == true) {
            arguments?.shouldAnimate = false
            view.alpha = 0f
            view.waitForPreDraw {
                view.translationY = view.height.toFloat()
                view
                    .animate()
                    .translationY(0f)
                    .alpha(1f)
                    .apply {
                        startDelay = 300
                        duration = 600
                    }
                    .start()
                getCampfireActivity().onScreenChanged()
                getCampfireActivity().openNavigationDrawer()
                false
            }
        }
        savedInstanceState?.let {
            viewModel.placeholderText.set(it.placeholderText)
            viewModel.buttonText.set(it.buttonText)
            viewModel.buttonIcon.set(it.buttonIcon)
            wasLastTransitionForACollection = it.wasLastTransitionForACollection
            viewModel.randomCollections = it.randomCollections
            viewModel.randomSongs = it.randomSongs
        }
        viewModel.shouldShowUpdateErrorSnackbar.onEventTriggered(this) {
            showSnackbar(
                message = R.string.home_update_error,
                action = { viewModel.updateData() })
        }
        viewModel.downloadSongError.onEventTriggered(this) { song ->
            song?.let {
                binding.root.post {
                    if (isAdded) {
                        showSnackbar(
                            message = getCampfireActivity().getString(R.string.songs_song_download_error, song.title),
                            action = { viewModel.downloadSong(song) })
                    }
                }
            }
        }
        viewModel.isLoading.onPropertyChanged(this) {
            if (viewModel.state.get() == StateLayout.State.NORMAL) {
                binding.swipeRefreshLayout.isRefreshing = it
            }
        }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener {
                analyticsManager.onSwipeToRefreshUsed(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
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
        viewModel.adapter.apply {
            collectionClickListener = { position, clickedView, image ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    (items[position] as? CollectionListItemViewModel.CollectionViewModel)?.collection?.let {
                        if (items.size > 1) {
                            linearLayoutManager.isScrollEnabled = false
                            viewModel.isDetailScreenOpen = true
                        }
                        getCampfireActivity().isUiBlocked = true
                        viewModel.collectionRepository.onCollectionOpened(it.id)
                        getCampfireActivity().openCollectionDetailsScreen(it, clickedView, image, items.size > 1)
                        wasLastTransitionForACollection = true
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
            songClickListener = { position, clickedView ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    (items[position] as? SongListItemViewModel.SongViewModel)?.song?.let {
                        if (items.size > 1) {
                            linearLayoutManager.isScrollEnabled = false
                            viewModel.isDetailScreenOpen = true
                        }
                        getCampfireActivity().isUiBlocked = true
                        getCampfireActivity().openDetailScreen(
                            clickedView,
                            if (position > viewModel.firstRandomSongIndex) viewModel.displayedRandomSongs else listOf(it),
                            items.size > 1,
                            if (position > viewModel.firstRandomSongIndex) position - viewModel.firstRandomSongIndex - 2 else 0,
                            true
                        )
                        wasLastTransitionForACollection = false
                    }
                }
            }
            downloadActionClickListener = { position ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    (items[position] as? SongListItemViewModel.SongViewModel)?.let {
                        analyticsManager.onDownloadButtonPressed(it.song.id)
                        viewModel.downloadSong(it.song)
                    }
                }
            }
            playlistActionClickListener = { position ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    (items[position] as? SongListItemViewModel.SongViewModel)?.let {
                        if (viewModel.areThereMoreThanOnePlaylists()) {
                            getCampfireActivity().isUiBlocked = true
                            PlaylistChooserBottomSheetFragment.show(childFragmentManager, it.song.id, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                        } else {
                            viewModel.toggleFavoritesState(it.song.id)
                        }
                    }
                }
            }
        }
        binding.recyclerView.addOnLayoutChangeListener(
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    binding.recyclerView.removeOnLayoutChangeListener(this)
                    if (reenterTransition != null) {
                        var index =
                            viewModel.adapter.items.indexOfFirst { it is CollectionListItemViewModel.CollectionViewModel && it.collection.id == getCampfireActivity().lastCollectionId }
                        if (index != RecyclerView.NO_POSITION) {
                            val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                            if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                linearLayoutManager.isScrollEnabled = true
                                binding.recyclerView.run { post { if (isAdded) scrollToPosition(index) } }
                            }
                        } else {
                            index = viewModel.adapter.items.indexOfFirst { it is SongListItemViewModel.SongViewModel && it.song.id == getCampfireActivity().lastSongId }
                            if (index != RecyclerView.NO_POSITION) {
                                val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                                if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                    linearLayoutManager.isScrollEnabled = true
                                    binding.recyclerView.run { post { if (isAdded) scrollToPosition(index) } }
                                }
                            }
                        }
                    }
                }
            })
        (view.parent as? ViewGroup)?.run {
            viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver?.removeOnPreDrawListener(this)
                    (sharedElementEnterTransition as? Transition)?.addListener(object : Transition.TransitionListener {

                        override fun onTransitionStart(transition: Transition?) = Unit

                        override fun onTransitionResume(transition: Transition?) = Unit

                        override fun onTransitionPause(transition: Transition?) = Unit

                        override fun onTransitionEnd(transition: Transition?) {
                            getCampfireActivity().isUiBlocked = false
                            transition?.removeListener(this)
                        }

                        override fun onTransitionCancel(transition: Transition?) {
                            getCampfireActivity().isUiBlocked = false
                            transition?.removeListener(this)
                        }
                    })
                    binding.recyclerView.postDelayed({ if (isAdded) parentFragment?.startPostponedEnterTransition() }, 50)
                    return true
                }
            })
            requestLayout()
        }
        getCampfireActivity().showPlayStoreRatingDialogIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.placeholderText = viewModel.placeholderText.get()
        outState.buttonText = viewModel.buttonText.get()
        outState.buttonIcon = viewModel.buttonIcon.get()
        outState.wasLastTransitionForACollection = wasLastTransitionForACollection
        outState.randomCollections = ArrayList(viewModel.randomCollections.take(HomeViewModel.RANDOM_COLLECTION_COUNT + HomeViewModel.NEW_COLLECTION_COUNT + 1))
        outState.randomSongs = ArrayList(viewModel.randomSongs.take(HomeViewModel.RANDOM_SONG_COUNT + HomeViewModel.NEW_SONG_COUNT + 1))
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun updateUI() {
        super.updateUI()
        linearLayoutManager.isScrollEnabled = true
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.new_collections -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_NEW_COLLECTIONS, it)
                shouldShowNewCollections = it
            }, { shouldShowNewCollections })
            R.id.new_songs -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_NEW_SONGS, it)
                shouldShowNewSongs = it
            }, { shouldShowNewSongs })
            R.id.random_collections -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_RANDOM_COLLECTIONS, it)
                shouldShowRandomCollections = it
            }, { shouldShowRandomCollections })
            R.id.random_songs -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_RANDOM_SONGS, it)
                shouldShowRandomSongs = it
            }, { shouldShowRandomSongs })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_EXPLICIT, it)
                shouldShowExplicit = it
            }, { shouldShowExplicit })
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            analyticsManager.onHomeFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_LANGUAGE + languageId, contains(languageId))
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }
}