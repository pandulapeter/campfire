package com.pandulapeter.campfire.feature.home.downloaded

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.DownloadedBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.dimension
import dagger.android.support.DaggerFragment
import javax.inject.Inject

/**
 * Displays the list of all downloaded songs. The list is searchable and filterable and contains
 * headers. The items are automatically updated after a period or manually using the pull-to-refresh
 * gesture. Items can be removed using the swipe-to-dismiss gesture.
 *
 * Controlled by [DownloadedViewModel].
 */
class DownloadedFragment : DaggerFragment() {

    @Inject lateinit var songInfoRepository: SongInfoRepository
    private lateinit var binding: DownloadedBinding
    private val viewModel by lazy { DownloadedViewModel(songInfoRepository) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_downloaded, container, false)
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
}