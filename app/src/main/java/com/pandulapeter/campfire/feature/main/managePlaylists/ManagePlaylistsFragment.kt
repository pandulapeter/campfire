package com.pandulapeter.campfire.feature.main.managePlaylists

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.databinding.FragmentManagePlaylistsBinding
import com.pandulapeter.campfire.feature.main.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.visibleOrGone
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ManagePlaylistsFragment : CampfireFragment<FragmentManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists),
    TopLevelFragment, BaseDialogFragment.OnDialogItemSelectedListener {

    override val topLevelBehavior = TopLevelBehavior(getCampfireActivity = { getCampfireActivity() })
    override val viewModel by viewModel<ManagePlaylistsViewModel>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val deleteAllButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_delete) {
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
    var managePlaylistAdapter: ManagePlaylistListAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.stateLayout.animateFirstView = savedInstanceState == null
        managePlaylistAdapter = ManagePlaylistListAdapter()
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS)
        topLevelBehavior.onViewCreated(savedInstanceState)
        topLevelBehavior.defaultToolbar.updateToolbarTitle(R.string.main_manage_playlists, getString(R.string.loading))
        getCampfireActivity()?.updateToolbarButtons(listOf(deleteAllButton))
        getCampfireActivity()?.updateFloatingActionButtonDrawable(requireContext().drawable(R.drawable.ic_add))
        binding.recyclerView.apply {
            setHasFixedSize(true)
            adapter = managePlaylistAdapter
            layoutManager = LinearLayoutManager(getCampfireActivity())
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }
        viewModel.state.observe { viewModel.playlistCount.value?.let { playlistCount -> updateToolbarTitle(playlistCount) } }
        viewModel.playlistCount.observe {
            updateToolbarTitle(it)
            showHintIfNeeded()
            if (it < Playlist.MAXIMUM_PLAYLIST_COUNT) {
                getCampfireActivity()?.enableFloatingActionButton()
            } else {
                getCampfireActivity()?.disableFloatingActionButton()
            }
        }
        viewModel.shouldShowDeleteAllButton.observe {
            deleteAllButton.visibleOrGone = it
            showHintIfNeeded()
        }
        val itemTouchHelper = ItemTouchHelper(object : ElevationItemTouchHelperCallback((context?.dimension(R.dimen.content_padding) ?: 0).toFloat()) {

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                if (viewHolder.adapterPosition > 0 && !binding.recyclerView.isAnimating)
                    makeMovementFlags(
                        if (managePlaylistAdapter?.itemCount ?: 0 > 2) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    ) else 0

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) =
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
                    managePlaylistAdapter?.items?.get(position)?.playlist?.let { playlist ->
                        if (position != RecyclerView.NO_POSITION) {
                            analyticsManager.onSwipeToDismissUsed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS)
                            viewModel.deletePlaylistPermanently()
                            firstTimeUserExperienceManager.managePlaylistsSwipeCompleted = true
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
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        managePlaylistAdapter?.dragHandleTouchListener = { position -> binding.recyclerView.findViewHolderForAdapterPosition(position)?.let { itemTouchHelper.startDrag(it) } }
        viewModel.items.observe { managePlaylistAdapter?.items = it }
        viewModel.moveEvent.observeAndReset { managePlaylistAdapter?.notifyItemMoved(it.first, it.second) }
    }

    override fun onResume() {
        super.onResume()
        showHintIfNeeded()
    }

    override fun onDestroyView() {
        managePlaylistAdapter = null
        super.onDestroyView()
    }

    override fun onBackPressed() = isUiBlocked

    override fun onFloatingActionButtonPressed() {
        hideSnackbar()
        NewPlaylistDialogFragment.show(childFragmentManager, AnalyticsManager.PARAM_VALUE_FLOATING_ACTION_BUTTON)
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_DELETE_ALL_CONFIRMATION) {
            analyticsManager.onDeleteAllButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS, managePlaylistAdapter?.itemCount ?: 0)
            viewModel.deleteAllPlaylists()
            showSnackbar(R.string.manage_playlists_all_playlists_deleted)
        }
    }

    private fun updateToolbarTitle(playlistCount: Int) = topLevelBehavior.defaultToolbar.updateToolbarTitle(
        R.string.main_manage_playlists,
        resources.getQuantityString(R.plurals.manage_playlists_subtitle, playlistCount, playlistCount)
    )

    private fun showHintIfNeeded() {

        fun showSwipeHintIfNeeded() {
            if (!firstTimeUserExperienceManager.managePlaylistsSwipeCompleted && !isSnackbarVisible() && viewModel.shouldShowDeleteAllButton.value == true) {
                showHint(
                    message = R.string.manage_playlists_hint_swipe,
                    action = { firstTimeUserExperienceManager.managePlaylistsSwipeCompleted = true }
                )
            }
        }

        fun showDragHintIfNeeded() {
            if (!firstTimeUserExperienceManager.managePlaylistsDragCompleted && !isSnackbarVisible() && (viewModel.playlistCount.value ?: 0) > 2) {
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

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 6
    }
}