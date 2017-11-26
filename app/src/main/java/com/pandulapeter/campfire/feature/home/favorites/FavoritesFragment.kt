package com.pandulapeter.campfire.feature.home.favorites

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.FavoritesBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.dimension

/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloaded.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [FavoritesViewModel].
 */
class FavoritesFragment : HomeFragment<FavoritesBinding, FavoritesViewModel>(R.layout.fragment_favorites) {

    override val viewModel by lazy { FavoritesViewModel(callbacks, songInfoRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            // Setup list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                viewModel.adapter.items[position].songInfo.let { songInfo ->
                    startActivity(DetailActivity.getStartIntent(context, songInfo.id, songInfo.title, songInfo.artist))
                }
            }
            // Setup swipe-to-dismiss functionality.
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.START or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.END) {
                override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = consume {
                    viewHolder?.adapterPosition?.let { originalPosition ->
                        target?.adapterPosition?.let { targetPosition ->
                            viewModel.swapSongPositions(originalPosition, targetPosition)
                        }
                    }
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                    viewHolder?.adapterPosition?.let { position ->
                        val songInfo = viewModel.adapter.items[position].songInfo
                        viewModel.removeSongFromFavorites(songInfo)
                        Snackbar
                            .make(binding.root, getString(R.string.favorites_song_removed, songInfo.title), Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo, { viewModel.addSongToFavorites(songInfo, position) })
                            .show()
                    }
                }

            }).attachToRecyclerView(binding.recyclerView)
        }
    }
}