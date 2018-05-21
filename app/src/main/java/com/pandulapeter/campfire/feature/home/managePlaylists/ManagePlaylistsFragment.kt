package com.pandulapeter.campfire.feature.home.managePlaylists

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.databinding.FragmentManagePlaylistsBinding
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

class ManagePlaylistsFragment : TopLevelFragment<FragmentManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists),
    BaseDialogFragment.OnDialogItemSelectedListener {

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 6
    }

    override val viewModel = ManagePlaylistsViewModel()
    override val canScrollToolbar = false
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val deleteAllButton by lazy {
        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_delete_24dp) {
            AlertDialogFragment.show(
                DIALOG_ID_DELETE_ALL_CONFIRMATION,
                childFragmentManager,
                R.string.are_you_sure,
                R.string.manage_playlists_delete_all_confirmation_message,
                R.string.manage_playlists_delete_all_confirmation_clear,
                R.string.cancel
            )
        }.apply {
            visibleOrGone = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_playlists, getString(R.string.loading))
        getCampfireActivity().updateToolbarButtons(listOf(deleteAllButton))
        getCampfireActivity().updateFloatingActionButtonDrawable(getCampfireActivity().drawable(R.drawable.ic_add_24dp))
        binding.recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(getCampfireActivity())
        }
        viewModel.state.onPropertyChanged(this) { updateToolbarTitle(viewModel.playlistCount.get()) }
        viewModel.playlistCount.onPropertyChanged(this) {
            updateToolbarTitle(it)
            showHintIfNeeded()
            if (it < Playlist.MAXIMUM_PLAYLIST_COUNT) {
                getCampfireActivity().enableFloatingActionButton()
            } else {
                getCampfireActivity().disableFloatingActionButton()
            }
        }
        viewModel.shouldShowDeleteAllButton.onPropertyChanged(this) {
            deleteAllButton.visibleOrGone = it
            showHintIfNeeded()
        }
        viewModel.adapter.run { itemClickListener = { getCampfireActivity().openPlaylistScreen(items[it].playlist.id) } }
        val itemTouchHelper = ItemTouchHelper(object : ElevationItemTouchHelperCallback((context?.dimension(R.dimen.content_padding) ?: 0).toFloat()) {

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                if (viewHolder.adapterPosition > 0)
                    makeMovementFlags(
                        if (viewModel.adapter.itemCount > 2) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    ) else 0

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) =
                consume {
                    viewHolder.adapterPosition.let { originalPosition ->
                        target.adapterPosition.let { targetPosition ->
                            if (originalPosition > 0 && targetPosition > 0) {
                                if (viewModel.hasPlaylistToDelete() || !firstTimeUserExperienceManager.managePlaylistsDragCompleted) {
                                    hideSnackbar()
                                }
                                firstTimeUserExperienceManager.managePlaylistsDragCompleted = true
                                viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                                binding.root.postDelayed({ if (isAdded) showHintIfNeeded() }, 300)
                                analyticsManager.onDragToRearrangeUsed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS)
                            }
                        }
                    }
                }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        analyticsManager.onSwipeToDismissUsed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS)
                        viewModel.deletePlaylistPermanently()
                        firstTimeUserExperienceManager.managePlaylistsSwipeCompleted = true
                        val playlist = viewModel.adapter.items[position].playlist
                        showSnackbar(
                            message = getString(R.string.manage_playlists_playlist_deleted_message, playlist.title),
                            actionText = R.string.undo,
                            action = {
                                analyticsManager.onUndoButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS)
                                viewModel.cancelDeletePlaylist()
                            },
                            dismissAction = { viewModel.deletePlaylistPermanently() }
                        )
                        viewModel.deletePlaylistTemporarily(playlist.id)
                    }
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        viewModel.adapter.dragHandleTouchListener = { position -> itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position)) }
    }

    override fun onResume() {
        super.onResume()
        showHintIfNeeded()
    }

    override fun onFloatingActionButtonPressed() {
        hideSnackbar()
        NewPlaylistDialogFragment.show(childFragmentManager, AnalyticsManager.PARAM_VALUE_FLOATING_ACTION_BUTTON)
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_DELETE_ALL_CONFIRMATION) {
            analyticsManager.onDeleteAllButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS, viewModel.adapter.itemCount)
            viewModel.deleteAllPlaylists()
            showSnackbar(R.string.manage_playlists_all_playlists_deleted)
        }
    }

    private fun updateToolbarTitle(playlistCount: Int) = defaultToolbar.updateToolbarTitle(
        R.string.home_manage_playlists,
        getCampfireActivity().resources.getQuantityString(R.plurals.manage_playlists_subtitle, playlistCount, playlistCount)
    )

    private fun showHintIfNeeded() {

        fun showSwipeHintIfNeeded() {
            if (!firstTimeUserExperienceManager.managePlaylistsSwipeCompleted && !isSnackbarVisible() && viewModel.shouldShowDeleteAllButton.get()) {
                showHint(
                    message = R.string.manage_playlists_hint_swipe,
                    action = { firstTimeUserExperienceManager.managePlaylistsSwipeCompleted = true }
                )
            }
        }

        fun showDragHintIfNeeded() {
            if (!firstTimeUserExperienceManager.managePlaylistsDragCompleted && !isSnackbarVisible() && viewModel.playlistCount.get() > 2) {
                showHint(
                    message = R.string.manage_playlists_hint_drag,
                    action = {
                        firstTimeUserExperienceManager.managePlaylistsDragCompleted = true
                        binding.root.postDelayed({ if (isAdded) showSwipeHintIfNeeded() }, 300)
                    }
                )
            }
        }

        if (!firstTimeUserExperienceManager.managePlaylistsDragCompleted) {
            showDragHintIfNeeded()
        } else {
            showSwipeHintIfNeeded()
        }
    }
}