package com.pandulapeter.campfire.feature.home.collections.detail

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.databinding.ItemCollectionBinding
import com.pandulapeter.campfire.feature.home.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class CollectionDetailFragment : SongListFragment<CollectionDetailViewModel>() {

    companion object {
        private var Bundle.collection by BundleArgumentDelegate.Parcelable("collection")

        fun newInstance(collection: Collection) = CollectionDetailFragment().withArguments {
            it.collection = collection
        }
    }

    override val viewModel by lazy {
        CollectionDetailViewModel(
            mainActivity,
            (arguments?.collection as? Collection) ?: throw IllegalStateException("No Collection specified.")
        ) { mainActivity.updateToolbarButtons(listOf(shuffleButton)) }
    }
    private val shuffleButton: ToolbarButton by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_shuffle_24dp) { shuffleSongs() } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        defaultToolbar.updateToolbarTitle(R.string.home_collections)
        val sharedElementBinding = DataBindingUtil.inflate<ItemCollectionBinding>(LayoutInflater.from(mainActivity), R.layout.item_collection, null, false)
        sharedElementBinding.viewModel = CollectionListItemViewModel.CollectionViewModel(viewModel.collection)
        binding.mainContainer.addView(sharedElementBinding.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }
}