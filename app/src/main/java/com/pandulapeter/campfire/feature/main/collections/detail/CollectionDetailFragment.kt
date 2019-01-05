package com.pandulapeter.campfire.feature.main.collections.detail

import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.View
import androidx.core.app.SharedElementCallback
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.detail.FadeInTransition
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.withArguments
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class CollectionDetailFragment : BaseSongListFragment<CollectionDetailViewModel>() {

    override val shouldSendMultipleSongs = true
    override val shouldShowManagePlaylist = true
    override val viewModel by viewModel<CollectionDetailViewModel> {
        parametersOf((arguments?.collection as? Collection) ?: throw IllegalStateException("No Collection specified."))
    }
    private val drawableBookmarkedToNotBookmarked by lazy { requireContext().animatedDrawable(R.drawable.avd_bookmarked_to_not_bookmarked) }
    private val drawableNotBookmarkedToBookmarked by lazy { requireContext().animatedDrawable(R.drawable.avd_not_bookmarked_to_bookmarked) }
    private val shareButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_share) {
            viewModel.collection.value?.collection?.songs?.let { songIds ->
                analyticsManager.onShareButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL, songIds.size)
                shareSongs(songIds)
            }
        }.apply { visibleOrGone = false }
    }
    private val shuffleButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_shuffle) {
            shuffleSongs(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL)
        }.apply { visibleOrGone = false }
    }
    private val bookmarkedButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(if (viewModel.collection.value?.collection?.isBookmarked == true) R.drawable.ic_bookmarked else R.drawable.ic_not_bookmarked) {
            viewModel.collection.value?.collection?.let { collection ->
                viewModel.collectionRepository.toggleBookmarkedState(collection.id)
                analyticsManager.onCollectionBookmarkedStateChanged(collection.id, collection.isBookmarked == true, AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL)
                bookmarkedButton.setImageDrawable((if (collection.isBookmarked == true) drawableNotBookmarkedToBookmarked else drawableBookmarkedToNotBookmarked).apply { this?.start() })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun createTransition(delay: Long) = TransitionSet()
            .addTransition(FadeInTransition())
            .addTransition(ChangeBounds())
            .addTransition(ChangeClipBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeImageTransform())
            .apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                startDelay = delay
                duration = DetailFragment.TRANSITION_DURATION
            }
        sharedElementEnterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
        sharedElementReturnTransition = createTransition(0)
        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                sharedElements[names[0]] = binding.collection
                sharedElements[names[1]] = binding.image
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        viewModel.onDataLoaded.observeAndReset {
            shareButton.visibleOrGone = true
            shuffleButton.visibleOrGone = true
        }
        getCampfireActivity()?.updateToolbarButtons(listOf(bookmarkedButton, shuffleButton, shareButton))
        viewModel.collection.value?.collection?.let {
            analyticsManager.onCollectionDetailScreenOpened(it.id)
            val songCount = it.songs?.size ?: 0
            topLevelBehavior.defaultToolbar.updateToolbarTitle(
                it.title, if (songCount == 0) {
                    getString(R.string.manage_playlists_song_count_empty)
                } else {
                    resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    companion object {
        private var Bundle.collection by BundleArgumentDelegate.Parcelable("collection")

        fun newInstance(collection: Collection) = CollectionDetailFragment().withArguments {
            it.collection = collection
        }
    }
}