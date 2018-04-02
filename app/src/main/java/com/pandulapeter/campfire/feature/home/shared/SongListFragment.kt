package com.pandulapeter.campfire.feature.home.shared

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentSongListBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.old.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.old.util.onEventTriggered
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onPropertyChanged

abstract class SongListFragment<out VM : SongListViewModel> : TopLevelFragment<FragmentSongListBinding, VM>(R.layout.fragment_song_list) {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.adapter.run { itemClickListener = { position, clickedView ->  mainActivity.openDetailScreen(clickedView, items[position].song.id) } }
        viewModel.shouldShowErrorSnackbar.onEventTriggered { showSnackbar(R.string.library_update_error, View.OnClickListener { viewModel.updateData() }) }
        viewModel.isLoading.onPropertyChanged { binding.swipeRefreshLayout.isRefreshing = it }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener { viewModel.updateData() }
            setColorSchemeColors(context.color(R.color.accent))
        }
        binding.recyclerView.run {
            setHasFixedSize(true)
            addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 0 && recyclerView?.isAnimating == false) {
                        hideKeyboard(activity?.currentFocus)
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        binding.swipeRefreshLayout.isRefreshing = viewModel.isLoading.get()
    }

    override fun onPause() {
        //TODO: Bug: start refreshing and quickly open the options screen.
        binding.swipeRefreshLayout.isRefreshing = false
        binding.swipeRefreshLayout.clearAnimation()
        binding.swipeRefreshLayout.destroyDrawingCache()
        super.onPause()
    }
}