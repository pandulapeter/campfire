package com.pandulapeter.campfire.feature.main.shared.baseSongList

import android.os.Bundle
import android.transition.Transition
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.core.app.SharedElementCallback
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentBaseSongListBinding
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.waitForPreDraw

abstract class BaseSongListFragment<out VM : BaseSongListViewModel> : CampfireFragment<FragmentBaseSongListBinding, VM>(R.layout.fragment_base_song_list), TopLevelFragment {

    override val topLevelBehavior = TopLevelBehavior(getCampfireActivity = { getCampfireActivity() })
    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    protected lateinit var linearLayoutManager: DisableScrollLinearLayoutManager
    protected open val canOpenDetailScreen = true
    protected open val shouldSendMultipleSongs = false
    protected open val shouldShowManagePlaylist = true
    protected var recyclerAdapter: RecyclerAdapter? = null
    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy > 0 && !recyclerView.isAnimating) {
                hideKeyboard(activity?.currentFocus)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                val index = recyclerAdapter?.items?.indexOfFirst { it is SongItemViewModel && it.song.id == getCampfireActivity()?.lastSongId } ?: RecyclerView.NO_POSITION
                if (index != RecyclerView.NO_POSITION) {
                    (binding.recyclerView.findViewHolderForAdapterPosition(index)
                        ?: binding.recyclerView.findViewHolderForAdapterPosition(linearLayoutManager.findLastVisibleItemPosition()))?.let {
                        sharedElements[names[0]] = it.itemView
                        getCampfireActivity()?.lastSongId = ""
                    }
                }
            }
        })
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerAdapter = RecyclerAdapter()
        binding.stateLayout.animateFirstView = savedInstanceState == null
        postponeEnterTransition()
        if (savedInstanceState != null) {
            viewModel.buttonText.value = savedInstanceState.buttonText
        }
        topLevelBehavior.onViewCreated(savedInstanceState)
        recyclerAdapter?.run {
            songClickListener = { song, position, clickedView ->
                if (!isUiBlocked && canOpenDetailScreen) {
                    if (items.size > 1) {
                        viewModel.isDetailScreenOpen = true
                    }
                    isUiBlocked = true
                    onDetailScreenOpened()
                    getCampfireActivity()?.openDetailScreen(
                        clickedView,
                        if (shouldSendMultipleSongs) items.filterIsInstance<SongItemViewModel>().map { it.song } else listOf(song),
                        items.size > 1,
                        if (shouldSendMultipleSongs) position else 0,
                        shouldShowManagePlaylist
                    )
                }
            }
            songPlaylistClickListener = { song ->
                if (!isUiBlocked) {
                    if (viewModel.areThereMoreThanOnePlaylists()) {
                        isUiBlocked = true
                        PlaylistChooserBottomSheetFragment.show(childFragmentManager, song.id, viewModel.screenName)
                    } else {
                        viewModel.toggleFavoritesState(song.id)
                    }
                }
            }
            songDownloadClickListener = { song ->
                if (!isUiBlocked) {
                    analyticsManager.onDownloadButtonPressed(song.id)
                    viewModel.downloadSong(song)
                }
            }
        }
        viewModel.shouldShowUpdateErrorSnackbar.observeAndReset {
            showSnackbar(
                message = R.string.something_went_wrong,
                action = { viewModel.updateData() })
        }
        viewModel.downloadSongError.observeAndReset { song ->
            binding.root.post {
                if (isAdded) {
                    showSnackbar(
                        message = getString(R.string.songs_song_download_error, song.title),
                        action = { viewModel.downloadSong(song) })
                }
            }
        }
        viewModel.shouldScrollToTop.observeAndReset { recyclerAdapter?.shouldScrollToTop = it }
        viewModel.items.observeNotNull { recyclerAdapter?.items = it }
        viewModel.changeEvent.observeAndReset { recyclerAdapter?.notifyItemChanged(it.first, it.second) }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener {
                analyticsManager.onSwipeToRefreshUsed(viewModel.screenName)
                viewModel.updateData()
            }
            setColorSchemeColors(context.color(R.color.accent))
        }
        linearLayoutManager = DisableScrollLinearLayoutManager(requireContext()).apply { interactionBlocker = viewModel.interactionBlocker }
        binding.recyclerView.run {
            layoutManager = linearLayoutManager
            adapter = recyclerAdapter
            setHasFixedSize(true)
            addOnScrollListener(onScrollListener)
            addOnLayoutChangeListener(
                object : OnLayoutChangeListener {
                    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                        binding.recyclerView.removeOnLayoutChangeListener(this)
                        if (reenterTransition != null) {
                            val index =
                                recyclerAdapter?.items?.indexOfFirst { it is SongItemViewModel && it.song.id == getCampfireActivity()?.lastSongId } ?: RecyclerView.NO_POSITION
                            if (index != RecyclerView.NO_POSITION) {
                                val viewAtPosition = linearLayoutManager.findViewByPosition(index)
                                if (viewAtPosition == null || linearLayoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                    binding.recyclerView.run { post { if (isAdded) scrollToPosition(index) } }
                                }
                            }
                        }
                    }
                })
            itemAnimator = object : DefaultItemAnimator() {
                init {
                    supportsChangeAnimations = false
                }
            }
        }
        (view.parent as? ViewGroup)?.waitForPreDraw {
            consume {
                (sharedElementEnterTransition as? Transition)?.addListener(object : Transition.TransitionListener {

                    override fun onTransitionStart(transition: Transition?) = Unit

                    override fun onTransitionResume(transition: Transition?) = Unit

                    override fun onTransitionPause(transition: Transition?) = Unit

                    override fun onTransitionEnd(transition: Transition?) {
                        isUiBlocked = false
                        transition?.removeListener(this)
                    }

                    override fun onTransitionCancel(transition: Transition?) {
                        isUiBlocked = false
                        transition?.removeListener(this)
                    }
                })
                startPostponedEnterTransition()
            }
        }
    }

    override fun onBackPressed() = isUiBlocked

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.buttonText = viewModel.buttonText.value ?: 0
    }

    override fun onDestroyView() {
        binding.recyclerView.removeOnScrollListener(onScrollListener)
        recyclerAdapter = null
        super.onDestroyView()
    }

    protected open fun onDetailScreenOpened() = Unit

    protected fun shuffleSongs(source: String) {
        val tempList = (recyclerAdapter?.items?.filterIsInstance<SongItemViewModel>()?.map { it.song } ?: emptyList()).toMutableList()
        tempList.shuffle()
        isUiBlocked = true
        analyticsManager.onShuffleButtonPressed(source, tempList.size)
        getCampfireActivity()?.openDetailScreen(null, tempList, false, 0, shouldShowManagePlaylist)
    }

    companion object {
        private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
    }
}