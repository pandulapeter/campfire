package com.pandulapeter.campfire.feature.home.shared.homefragment.list

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.util.dimension

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture.
 *
 * Controlled by [SongListFragment].
 */
abstract class SongListFragment<B : ViewDataBinding, out VM : SongListViewModel>(@LayoutRes layoutResourceId: Int) : HomeFragment<B, VM>(layoutResourceId) {

    protected abstract fun getRecyclerView(): RecyclerView

    //TODO: Add error state for incorrect downloads.
    //TODO: Add no-results state for the case when everything is filtered out.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            //TODO: Hide the keyboard on scroll events.
            getRecyclerView().run {
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            }
            // Setup list item click listeners.
            //TODO: Only send the list of song id-s from Playlists.
            viewModel.adapter.itemClickListener = { position ->
                startActivity(DetailActivity.getStartIntent(
                    context = context,
                    currentId = viewModel.adapter.items[position].songInfo.id,
                    ids = viewModel.adapter.items.map { it.songInfo.id }))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        songInfoRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        songInfoRepository.unsubscribe(viewModel)
    }
}