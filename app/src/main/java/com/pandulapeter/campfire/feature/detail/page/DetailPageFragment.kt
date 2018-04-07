package com.pandulapeter.campfire.feature.detail.page

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.databinding.FragmentDetailPageBinding
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class DetailPageFragment : CampfireFragment<FragmentDetailPageBinding, DetailPageViewModel>(R.layout.fragment_detail_page) {

    companion object {
        private var Bundle.song by BundleArgumentDelegate.Parcelable("song")

        fun newInstance(song: Song) = DetailPageFragment().withArguments {
            it.song = song
        }
    }

    override val viewModel by lazy { DetailPageViewModel(arguments?.song as Song) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (parentFragment as? DetailFragment)?.onDataLoaded()
    }
}