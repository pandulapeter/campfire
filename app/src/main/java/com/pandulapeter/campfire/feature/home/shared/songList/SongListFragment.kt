package com.pandulapeter.campfire.feature.home.shared.songList

import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentSongListBinding
import com.pandulapeter.campfire.feature.home.shared.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged

abstract class SongListFragment<out VM : SongListViewModel> : TopLevelFragment<FragmentSongListBinding, VM>(R.layout.fragment_song_list) {

    override val shouldDelaySubscribing get() = viewModel.isDetailScreenOpen
    private lateinit var linearLayoutManager: DisableScrollLinearLayoutManager

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        viewModel.adapter.run {
            itemClickListener = { position, clickedView ->
                (items[position] as? SongListItemViewModel.SongViewModel)?.let {
                    viewModel.isDetailScreenOpen = true
                    linearLayoutManager.isScrollEnabled = false
                    mainActivity.openDetailScreen(clickedView, listOf(it.song), items.size > 1)
                }
            }
            downloadActionClickListener = { position -> (viewModel.adapter.items[position] as? SongListItemViewModel.SongViewModel)?.let { viewModel.downloadSong(it.song) } }
        }
        viewModel.shouldShowUpdateErrorSnackbar.onEventTriggered {
            showSnackbar(
                message = R.string.library_update_error,
                action = { viewModel.updateData() })
        }
        viewModel.downloadSongError.onEventTriggered { song ->
            song?.let {
                showSnackbar(
                    message = mainActivity.getString(R.string.library_song_download_error, song.title),
                    action = { viewModel.downloadSong(song) })
            }
        }
        viewModel.isLoading.onPropertyChanged {
            if (viewModel.state.get() == StateLayout.State.NORMAL) {
                binding.swipeRefreshLayout.isRefreshing = it
            }
        }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener { viewModel.updateData() }
            setColorSchemeColors(context.color(R.color.accent))
        }
        binding.recyclerView.run {
            setHasFixedSize(true)
            linearLayoutManager = DisableScrollLinearLayoutManager(mainActivity)
            layoutManager = linearLayoutManager
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 0 && recyclerView?.isAnimating == false) {
                        hideKeyboard(activity?.currentFocus)
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
        }
    }

    override fun updateUI() {
        super.updateUI()
        linearLayoutManager.isScrollEnabled = true
    }
}