package com.pandulapeter.campfire.feature.home.history

import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.HistoryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.MainViewModel
import com.pandulapeter.campfire.feature.home.library.HeaderItemDecoration
import com.pandulapeter.campfire.feature.home.library.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.feature.shared.AlertDialogFragment
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.performAfterExpand
import javax.inject.Inject

/**
 * Allows the user to see the history of the songs they opened.
 *
 * Controlled by [HistoryViewModel].
 */
class HistoryFragment : SongListFragment<HistoryBinding, HistoryViewModel>(R.layout.fragment_history), AlertDialogFragment.OnDialogItemsSelectedListener {
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var historyRepository: HistoryRepository
    @Inject lateinit var firstTimeUserExperienceRepository: FirstTimeUserExperienceRepository

    override fun createViewModel() = HistoryViewModel(songInfoRepository, downloadedSongRepository, playlistRepository, historyRepository)

    override fun getAppBarLayout() = binding.appBarLayout

    override fun getRecyclerView() = binding.recyclerView

    override fun getCoordinatorLayout() = binding.coordinatorLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowConfirmationDialog.onEventTriggered {
            AlertDialogFragment.show(childFragmentManager,
                R.string.history_clear_confirmation_title,
                R.string.history_clear_confirmation_message,
                R.string.history_clear_confirmation_clear,
                R.string.cancel)
        }
        // Set up swipe-to-dismiss functionality.
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromHistory(songInfo.id)
                    firstTimeUserExperienceRepository.shouldShowHistoryHint = false
                    dismissHintSnackbar()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        // Fix a bug with updating the item decorations.
        viewModel.shouldInvalidateItemDecorations.onEventTriggered { binding.recyclerView.run { postDelayed({ invalidateItemDecorations() }, 100) } }
        context?.let { context ->
            // Set up the item headers.
            binding.recyclerView.addItemDecoration(object : HeaderItemDecoration(context) {
                override fun isHeader(position: Int) = position >= 0 && viewModel.isHeader(position)

                override fun getHeaderTitle(position: Int) = if (position >= 0) viewModel.getHeaderTitle(position).let { if (it == 0) "" else getString(it) } else ""
            })

            // Set up list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                binding.appBarLayout.performAfterExpand(onExpanded = {
                    (activity as? MainActivity)?.setNavigationItem(MainViewModel.MainNavigationItem.Detail(viewModel.adapter.items[position].songInfo.id))
                })
            }
            viewModel.adapter.playlistActionClickListener = { position ->
                viewModel.adapter.items[position].let { songInfoViewModel ->
                    val songId = songInfoViewModel.songInfo.id
                    if (playlistRepository.getPlaylists().size == 1) {
                        if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
                            playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
                        } else {
                            playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
                        }
                    } else {
                        PlaylistChooserBottomSheetFragment.show(childFragmentManager, songId)
                    }
                }
            }
            viewModel.adapter.downloadActionClickListener = { position -> viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) } }
        }
        viewModel.shouldShowHintSnackbar.onPropertyChanged {
            if (firstTimeUserExperienceRepository.shouldShowHistoryHint) {
                binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.history_hint) {
                    firstTimeUserExperienceRepository.shouldShowHistoryHint = false
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playlistRepository.subscribe(viewModel)
        historyRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
        historyRepository.unsubscribe(viewModel)
    }

    override fun onPositiveButtonSelected() {
        viewModel.clearHistory()
        dismissHintSnackbar()
    }
}