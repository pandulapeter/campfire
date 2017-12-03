package com.pandulapeter.campfire.feature.home.playlist

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.PlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.util.consume

/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloads.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [PlaylistViewModel].
 */
class PlaylistFragment : SongListFragment<PlaylistBinding, PlaylistViewModel>(R.layout.fragment_playlist) {

    override fun createViewModel() = PlaylistViewModel(callbacks, userPreferenceRepository, songInfoRepository, playlistRepository)

    override fun getRecyclerView() = binding.recyclerView

    //TODO: Add empty state placeholder.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup swipe-to-dismiss functionality.
        //TODO: Change the elevation of the card that's being dragged.
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.END) {

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = consume {
                viewHolder?.adapterPosition?.let { originalPosition ->
                    target?.adapterPosition?.let { targetPosition ->
                        viewModel.swapSongsInFavorites(originalPosition, targetPosition)
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromFavorites(songInfo.id)
                }
            }

        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        // Setup list item click listeners.
        viewModel.adapter.itemActionTouchListener = { position ->
            itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position))
        }
    }
}