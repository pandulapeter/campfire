package com.pandulapeter.campfire.feature.home.downloaded

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.*
import android.view.View
import com.pandulapeter.campfire.DownloadedBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.dimension

/**
 * Displays the list of all downloaded songs. The list is searchable and filterable and contains
 * headers. The items are automatically updated after a period or manually using the pull-to-refresh
 * gesture. Items can be removed using the swipe-to-dismiss gesture.
 *
 * Controlled by [DownloadedViewModel].
 */
class DownloadedFragment : HomeFragment<DownloadedBinding, DownloadedViewModel>(R.layout.fragment_downloaded) {

    override val viewModel by lazy { DownloadedViewModel(callbacks, songInfoRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            //TODO: Hide the keyboard on scroll events.
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            // Setup list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                viewModel.adapter.items[position].songInfo.let { songInfo ->
                    startActivity(DetailActivity.getStartIntent(context, songInfo.id, songInfo.title, songInfo.artist))
                }
            }
            viewModel.adapter.itemActionClickListener = { position ->
                viewModel.addOrRemoveSongFromFavorites(viewModel.adapter.items[position].songInfo)
            }
            // Setup swipe-to-dismiss functionality.
            ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) =
                    makeFlag(ACTION_STATE_SWIPE, LEFT or RIGHT)

                override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                    viewHolder?.adapterPosition?.let { position ->
                        val songInfo = viewModel.adapter.items[position].songInfo
                        viewModel.removeSongFromDownloaded(songInfo)
                        Snackbar
                            .make(binding.root, getString(R.string.downloaded_song_deleted, songInfo.title), Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo, { viewModel.addSongToDownloaded(songInfo) })
                            .show()
                    }
                }

            }).attachToRecyclerView(binding.recyclerView)
        }
    }

    override fun isSearchInputVisible() = binding.searchTitle.searchInputVisible

    override fun closeSearchInput() {
        binding.searchTitle.searchInputVisible = false
    }
}