package com.pandulapeter.campfire.feature.home.favorites

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.FavoritesBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.dimension
import dagger.android.support.DaggerFragment
import javax.inject.Inject

/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloaded.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [FavoritesViewModel].
 */
class FavoritesFragment : DaggerFragment() {

    @Inject lateinit var songInfoRepository: SongInfoRepository
    private lateinit var binding: FavoritesBinding
    private val viewModel by lazy { FavoritesViewModel(songInfoRepository) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_favorites, container, false)
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            // Setup list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                viewModel.adapter.items[position].let { songInfoViewModel ->
                    startActivity(DetailActivity.getStartIntent(context, songInfoViewModel.songInfo.title, songInfoViewModel.songInfo.artist))
                }
            }
            // Setup swipe-to-dismiss functionality.
            ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) =
                    makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

                override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                    viewHolder?.adapterPosition?.let { position ->
                        val songInfo = viewModel.adapter.items[position].songInfo
                        viewModel.removeSongFromFavorites(songInfo)
                        Snackbar
                            .make(binding.root, R.string.favorites_song_removed, Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo, { viewModel.addSongToFavorites(songInfo) })
                            .show()
                    }
                }

            }).attachToRecyclerView(binding.recyclerView)
        }
    }
}