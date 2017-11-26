package com.pandulapeter.campfire.feature.home.favorites

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.FavoritesBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.util.consume

/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloads.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [FavoritesViewModel].
 */
class FavoritesFragment : HomeFragment<FavoritesBinding, FavoritesViewModel>(R.layout.fragment_favorites) {

    override val viewModel by lazy { FavoritesViewModel(callbacks, songInfoRepository) }

    //TODO: Add empty state for not having any favorites (or everything being filtered out).
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup swipe-to-dismiss functionality.
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
                    Snackbar
                        .make(binding.root, getString(R.string.favorites_song_removed, songInfo.title), Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, { viewModel.addSongToFavorites(songInfo.id, position) })
                        .show()
                }
            }

        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        // Setup list item click listeners.
        viewModel.adapter.itemActionTouchListener = { position ->
            itemTouchHelper.startDrag(getRecyclerView().findViewHolderForAdapterPosition(position))
        }
    }

    override fun getRecyclerView() = binding.recyclerView

    override fun getSwipeRefreshLayout() = binding.swipeRefreshLayout

    override fun searchInputVisible() = false

    override fun closeSearchInput() = Unit
}