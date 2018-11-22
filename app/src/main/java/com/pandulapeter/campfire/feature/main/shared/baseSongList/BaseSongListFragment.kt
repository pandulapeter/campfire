package com.pandulapeter.campfire.feature.main.shared.baseSongList

import android.os.Bundle
import android.transition.Transition
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.CallSuper
import androidx.core.app.SharedElementCallback
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentBaseSongListBinding
import com.pandulapeter.campfire.feature.main.collections.detail.CollectionDetailViewModel
import com.pandulapeter.campfire.feature.main.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.shared.deprecated.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onEventTriggered


abstract class BaseSongListFragment<out VM : BaseSongListViewModel> : TopLevelFragment<FragmentBaseSongListBinding, VM>(R.layout.fragment_base_song_list) {

    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    protected lateinit var linearLayoutManager: DisableScrollLinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                val index = viewModel.adapter.items.indexOfFirst { it is SongListItemViewModel.SongViewModel && it.song.id == getCampfireActivity().lastSongId }
                if (index != RecyclerView.NO_POSITION) {
                    (binding.recyclerView.findViewHolderForAdapterPosition(index)
                        ?: binding.recyclerView.findViewHolderForAdapterPosition(linearLayoutManager.findLastVisibleItemPosition()))?.let {
                        sharedElements[names[0]] = it.itemView
                        getCampfireActivity().lastSongId = ""
                    }
                }
            }
        })
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        viewModel.adapter.run {
            itemClickListener = { position, clickedView ->
                if (linearLayoutManager.isScrollEnabled && !getCampfireActivity().isUiBlocked) {
                    (items[position] as? SongListItemViewModel.SongViewModel)?.let { songViewModel ->
                        if (items.size > 1) {
                            linearLayoutManager.isScrollEnabled = false
                            viewModel.isDetailScreenOpen = true
                        }
                        getCampfireActivity().isUiBlocked = true
                        onDetailScreenOpened()
                        val shouldSendMultipleSongs = viewModel is PlaylistViewModel || viewModel is CollectionDetailViewModel
                        getCampfireActivity().openDetailScreen(
                            clickedView,
                            if (shouldSendMultipleSongs) items.filterIsInstance<SongListItemViewModel.SongViewModel>().map { it.song } else listOf(songViewModel.song),
                            items.size > 1,
                            if (shouldSendMultipleSongs) position else 0,
                            viewModel !is PlaylistViewModel
                        )
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
                            PlaylistChooserBottomSheetFragment.show(childFragmentManager, it.song.id, viewModel.screenName)
                        } else {
                            viewModel.toggleFavoritesState(it.song.id)
                        }
                    }
                }
            }
        }
        viewModel.shouldShowUpdateErrorSnackbar.onEventTriggered(this) {
            showSnackbar(
                message = R.string.songs_update_error,
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
        binding.swipeRefreshLayout.run {
            setOnRefreshListener {
                analyticsManager.onSwipeToRefreshUsed(viewModel.screenName)
                viewModel.updateData()
            }
            setColorSchemeColors(context.color(R.color.accent))
        }
        linearLayoutManager = DisableScrollLinearLayoutManager(getCampfireActivity())
        binding.recyclerView.run {
            layoutManager = linearLayoutManager
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0 && !recyclerView.isAnimating) {
                        hideKeyboard(activity?.currentFocus)
                    }
                }
            })
            addOnLayoutChangeListener(
                object : OnLayoutChangeListener {
                    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                        binding.recyclerView.removeOnLayoutChangeListener(this)
                        if (reenterTransition != null) {
                            val index = viewModel.adapter.items.indexOfFirst { it is SongListItemViewModel.SongViewModel && it.song.id == getCampfireActivity().lastSongId }
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
            itemAnimator = object : DefaultItemAnimator() {
                init {
                    supportsChangeAnimations = false
                }
            }
        }
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
                    startPostponedEnterTransition()
                    return true
                }
            })
            requestLayout()
        }
    }

    override fun onBackPressed() = !linearLayoutManager.isScrollEnabled

    override fun updateUI() {
        super.updateUI()
        linearLayoutManager.isScrollEnabled = true
    }

    protected open fun onDetailScreenOpened() = Unit

    protected fun shuffleSongs(source: String) {
        val tempList = viewModel.adapter.items.filterIsInstance<SongListItemViewModel.SongViewModel>().map { it.song }.toMutableList()
        tempList.shuffle()
        analyticsManager.onShuffleButtonPressed(source, tempList.size)
        getCampfireActivity().openDetailScreen(null, tempList, false, 0, viewModel is CollectionDetailViewModel)
    }
}