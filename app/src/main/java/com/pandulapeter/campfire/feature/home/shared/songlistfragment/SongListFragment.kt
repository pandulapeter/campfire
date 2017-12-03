package com.pandulapeter.campfire.feature.home.shared.songlistfragment

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import javax.inject.Inject

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture.
 *
 * Controlled by subclasses of [SongListViewModel].
 */
abstract class SongListFragment<B : ViewDataBinding, out VM : SongListViewModel>(@LayoutRes layoutResourceId: Int) : HomeFragment<B, VM>(layoutResourceId) {
    @Inject lateinit var songInfoRepository: SongInfoRepository
    @Inject lateinit var playlistRepository: PlaylistRepository

    protected abstract fun getRecyclerView(): RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context?.let { context ->
            // Initialize the list.
            getRecyclerView().run {
                adapter = viewModel.adapter
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
                addOnScrollListener(object : RecyclerView.OnScrollListener() {

                    override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                        if (dy > 0) {
                            hideKeyboard(activity?.currentFocus)
                        }
                    }
                })
            }
            // Set up list item click listeners.
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
        playlistRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        songInfoRepository.unsubscribe(viewModel)
        playlistRepository.unsubscribe(viewModel)
    }
}